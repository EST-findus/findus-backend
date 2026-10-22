package com.findus.backend.news;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.findus.backend.news.client.NaverNewsClient;
import com.findus.backend.news.config.NewsProperties;
import com.findus.backend.news.exception.NewsException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@DisplayName("네이버 뉴스 검색 클라이언트: 인증 헤더·오류 매핑·응답 검증")
class NaverNewsClientTests {
    private HttpServer server;
    private final AtomicReference<HttpExchange> last=new AtomicReference<>();
    private int status=200;
    private String body="{\"total\":1,\"items\":[{\"title\":\"<b>실종</b>\"}]}";

    @AfterEach void stop() { if (server!=null) server.stop(0); }

    private NaverNewsClient client(String provider,String id,String secret) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/news",exchange -> {
            last.set(exchange);
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        var endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/news");
        return new NaverNewsClient(new NewsProperties(provider,id,secret,null,null,endpoint),new ObjectMapper());
    }

    @Test void hub는_전용_헤더와_format_json으로_요청하고_응답을_그대로_돌려준다() throws Exception {
        var result=client("hub","id","secret").search("장기 실종아동",11,10,"date");
        assertThat(result.path("items").get(0).path("title").asString()).isEqualTo("<b>실종</b>");
        var headers=last.get().getRequestHeaders();
        assertThat(headers.getFirst("X-NCP-APIGW-API-KEY-ID")).isEqualTo("id");
        assertThat(headers.getFirst("X-NCP-APIGW-API-KEY")).isEqualTo("secret");
        assertThat(last.get().getRequestURI().getQuery()).contains("query=","start=11","format=json");
    }

    @Test void developers는_개발자센터_헤더를_쓰고_format을_붙이지_않는다() throws Exception {
        client("developers","id","secret").search("q",1,10,"sim");
        assertThat(last.get().getRequestHeaders().getFirst("X-Naver-Client-Id")).isEqualTo("id");
        assertThat(last.get().getRequestURI().getQuery()).doesNotContain("format=");
    }

    @Test void 키가_없으면_요청하지_않고_503() throws Exception {
        var client=client("hub","","");
        assertThatThrownBy(() -> client.search("q",1,10,"date")).isInstanceOfSatisfying(NewsException.class,e -> assertThat(e.status().value()).isEqualTo(503));
        assertThat(last.get()).isNull();
    }

    @Test void 네이버_오류는_429_또는_502로_바꾸고_짧은_코드만_노출한다() throws Exception {
        status=429;body="{\"errorCode\":\"012\",\"errorMessage\":\"secret detail\"}";
        var client=client("hub","id","secret");
        assertThatThrownBy(() -> client.search("q",1,10,"date")).isInstanceOfSatisfying(NewsException.class,e -> {
            assertThat(e.status().value()).isEqualTo(429);
            assertThat(e.getMessage()).contains("코드 012").doesNotContain("secret detail");
        });
        status=401;body="not json";
        assertThatThrownBy(() -> client.search("q",1,10,"date")).isInstanceOfSatisfying(NewsException.class,e -> assertThat(e.status().value()).isEqualTo(502));
    }

    @Test void JSON이_아니거나_items가_배열이_아니면_502() throws Exception {
        body="<html>";
        var client=client("hub","id","secret");
        assertThatThrownBy(() -> client.search("q",1,10,"date")).isInstanceOfSatisfying(NewsException.class,e -> assertThat(e.status().value()).isEqualTo(502));
        body="{\"items\":{}}";
        assertThatThrownBy(() -> client.search("q",1,10,"date")).isInstanceOfSatisfying(NewsException.class,e -> assertThat(e.status().value()).isEqualTo(502));
        body="{\"items\":[1]}";
        assertThatThrownBy(() -> client.search("q",1,10,"date")).isInstanceOf(NewsException.class);
    }
}
