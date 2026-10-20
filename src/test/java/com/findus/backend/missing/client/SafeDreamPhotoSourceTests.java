package com.findus.backend.missing.client;

import com.findus.backend.missing.dto.SourcePerson;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("안전Dream 상세 사진 목록 수집")
class SafeDreamPhotoSourceTests {
    HttpServer server;
    URI base;
    final AtomicInteger calls = new AtomicInteger();
    final AtomicInteger imageCalls = new AtomicInteger();
    int imageFailures;
    String html = "<script>function fn_lcmMssImgChange(){}</script>";
    int status = 200;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/home/lcm/lcmMssGet.do", exchange -> {
            calls.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            String id = query.substring(query.indexOf("msspsnIdntfccd=") + "msspsnIdntfccd=".length());
            exchange.getResponseHeaders().add("Set-Cookie", "PHOTO_SESSION=" + id + "; Path=/; HttpOnly");
            byte[] body = html.replace("p=123", "p=" + id).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/home/lcm/blobImgListView.do", exchange -> {
            if (imageCalls.incrementAndGet() <= imageFailures) {
                exchange.sendResponseHeaders(503, -1); exchange.close(); return;
            }
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookie == null || !cookie.contains("PHOTO_SESSION=")) {
                exchange.sendResponseHeaders(403, -1); exchange.close(); return;
            }
            // 사진 서버는 p 값을 무시하고 쿠키에 저장된 아동을 선택합니다.
            int color = cookie.contains("456") ? 0x00aa00 : 0xaa0000;
            byte[] body = png(color);
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/lcm/imgView.do", exchange -> {
            byte[] body = png(0x0000aa);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
    static byte[] png(int color) throws java.io.IOException {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
    @AfterEach void stop() { server.stop(0); }
    SourcePerson person(String photo) {
        return new SourcePerson("123", "가상아동", "010", null, null, null, null, null, null, null, photo);
    }
    String image(String index) {
        return "<img src='/home/lcm/blobImgListView.do?tknphotoFileIdx=" + index + "&amp;p=123'>";
    }

    // region 전체 사진과 주소 검증
    @Test @DisplayName("대표 이미지 중복을 제거하고 실제 목록의 사진 3장을 순번대로 반환한다")
    void allListedPhotos() {
        html += image("2") + image("0") + image("1") + image("0");
        var result = new SafeDreamPhotoSource(base, 0).fetch(person("대표사진"));
        assertThat(result.status()).isEqualTo("DETAIL_LIST");
        assertThat(result.urls()).containsExactly(
                base + "/home/lcm/blobImgListView.do?tknphotoFileIdx=0&p=123",
                base + "/home/lcm/blobImgListView.do?tknphotoFileIdx=1&p=123",
                base + "/home/lcm/blobImgListView.do?tknphotoFileIdx=2&p=123");
        assertThat(calls).hasValue(1);
        assertThat(result.images()).hasSize(3).allMatch(i -> i.dataUrl().startsWith("data:image/png;base64,"));
    }

    @Test @DisplayName("다른 서버와 다른 사람의 사진을 제외하고 누락된 순번을 추측하지 않는다")
    void rejectsUnrelatedUrls() {
        html += image("0") + image("5")
                + "<img src='https://other.example/home/lcm/blobImgListView.do?tknphotoFileIdx=1&p=123'>"
                + "<img src='/home/lcm/blobImgListView.do?tknphotoFileIdx=2&p=999'>"
                + "<img src='/home/lcm/blobImgListView.do?tknphotoFileIdx=3&p=123&p=999'>";
        var result = new SafeDreamPhotoSource(base, 0).fetch(person(null));
        assertThat(result.urls()).hasSize(2).allMatch(u -> u.startsWith(base.toString()));
        assertThat(result.urls().get(1)).contains("tknphotoFileIdx=5");
    }
    // endregion

    @Test @DisplayName("쿠키 기반 사진 서버에서도 아동별 세션을 분리하고 받은 사진을 고정한다")
    void bindsImagesToTheirOwnSession() {
        html += image("0");
        var source = new SafeDreamPhotoSource(base, 0);
        var first = source.fetch(person(null));
        var other = new SourcePerson("456", "다른 가상아동", "010", null, null, null, null, null, null, null, null);
        var second = source.fetch(other);
        var again = source.fetch(person(null));
        assertThat(first.images()).hasSize(1);
        assertThat(second.images()).hasSize(1);
        assertThat(first.images().get(0).sha256()).isNotEqualTo(second.images().get(0).sha256());
        assertThat(first.images().get(0).dataUrl()).isEqualTo(again.images().get(0).dataUrl());
        assertThat(first.images().get(0).sourceUrl()).endsWith("p=123");
        assertThat(second.images().get(0).sourceUrl()).endsWith("p=456");
    }

    // region 사진 없음과 장애 시 대표 사진 유지
    @Test @DisplayName("사진의 일시 오류를 같은 세션에서 한 번 재시도하고 실제 요청 수에 포함한다")
    void retriesTransientImageFailure() {
        html += image("0");
        imageFailures = 1;
        var source = new SafeDreamPhotoSource(base, 0);
        var result = source.fetch(person(null));
        assertThat(result.status()).isEqualTo("DETAIL_LIST");
        assertThat(result.images()).hasSize(1);
        assertThat(source.detailRequests()).isEqualTo(1);
        assertThat(source.imageRequests()).isEqualTo(2);
    }

    @Test @DisplayName("정상 상세 화면에 사진이 없으면 사진 없음으로 표시한다")
    void noPhotos() {
        var result = new SafeDreamPhotoSource(base, 0).fetch(person(null));
        assertThat(result.status()).isEqualTo("NO_PHOTOS");
        assertThat(result.urls()).isEmpty();
    }

    @Test @DisplayName("상세 조회가 세 번 연속 실패하면 추가 요청 없이 API 대표 사진을 유지한다")
    void stopsAfterRepeatedFailures() {
        status = 503;
        var source = new SafeDreamPhotoSource(base, 0);
        for (int i = 0; i < 5; i++) {
            var result = source.fetch(person("https://www.safe182.go.kr/api/lcm/imgView.do?msspsnIdntfccd=123"));
            assertThat(result.status()).isEqualTo("API_FALLBACK");
            assertThat(result.urls()).hasSize(1);
        }
        assertThat(calls).hasValue(3);
    }

    @Test @DisplayName("HTTP 200 오류 화면을 정상적인 사진 없음으로 오인하지 않는다")
    void rejectsErrorHtml() {
        html = "<html><title>Sorry Page</title></html>";
        assertThat(new SafeDreamPhotoSource(base, 0).fetch(person(null)).status()).isEqualTo("UNCONFIRMED");
    }
    // endregion
}
