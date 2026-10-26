package com.findus.backend.aiserver;

import com.findus.backend.aiserver.client.AiServerClient;
import com.findus.backend.aiserver.exception.AiServerException;
import com.findus.backend.aiserver.exception.AiServerException.Reason;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("AI 서버 머리 스타일 호출")
class AiServerClientTests {
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
    HttpServer server;
    URI base;
    int status = 200;
    String body;
    final AtomicReference<String> request = new AtomicReference<>();
    final AtomicReference<String> apiKey = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        body = """
                {"result_id":"r1","kind":"hair","sex":"F","seconds":41.5,
                 "images":[{"key":"face","url":"/v1/results/r1/images/face.png","info":{"ref":"a.jpg","scale":1.0}}],
                 "extra":{"style":"bob"}}""";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/hair", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            send(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/results/", exchange -> send(exchange, 200, "image/png", PNG));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { server.stop(0); }

    static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    AiServerClient client() { return new AiServerClient(base, "secret", Duration.ofSeconds(5)); }

    @Test void 사진과_성별_스타일을_보내고_결과_PNG까지_받는다() {
        var result = client().hair(new byte[]{1, 2}, "F", "bob");

        assertThat(request.get()).contains("name=\"image\"", "filename=\"face.png\"", "name=\"sex\"", "name=\"style\"", "bob", "name=\"ref\"", "auto");
        assertThat(apiKey.get()).isEqualTo("secret");
        assertThat(result.resultId()).isEqualTo("r1");
        assertThat(result.png()).isEqualTo(PNG);
        assertThat(result.info()).containsEntry("ref", "a.jpg");
    }

    @Test void 얼굴을_못찾은_400은_AI서버_안내를_전달한다() {
        status = 400;
        body = "{\"detail\":\"얼굴을 찾지 못했습니다\"}";
        assertThatThrownBy(() -> client().hair(new byte[]{1}, "M", "short"))
                .isInstanceOfSatisfying(AiServerException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.BAD_INPUT);
                    assertThat(e.getMessage()).isEqualTo("얼굴을 찾지 못했습니다");
                });
    }

    @Test void GPU_대기초과_503은_BUSY다() {
        status = 503;
        body = "{\"detail\":\"busy\"}";
        assertThatThrownBy(() -> client().hair(new byte[]{1}, "M", "short"))
                .isInstanceOfSatisfying(AiServerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.BUSY));
    }

    @Test void 결과_경로가_아닌_주소는_따라가지_않는다() {
        body = body.replace("/v1/results/r1/images/face.png", "http://example.com/x.png");
        assertThatThrownBy(() -> client().hair(new byte[]{1}, "F", "bob"))
                .isInstanceOfSatisfying(AiServerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test void AI서버가_꺼져있으면_UNAVAILABLE이다() {
        server.stop(0);
        assertThatThrownBy(() -> client().hair(new byte[]{1}, "F", "bob"))
                .isInstanceOfSatisfying(AiServerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    }
}
