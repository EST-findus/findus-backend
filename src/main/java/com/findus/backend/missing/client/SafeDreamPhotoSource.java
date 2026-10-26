package com.findus.backend.missing.client;

import com.findus.backend.missing.dto.SourcePerson;
import java.io.StringReader;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.MessageDigest;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** 공개 상세 화면에 실제로 나열된 사진을 아동별 세션으로 수집합니다. */
public final class SafeDreamPhotoSource {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SafeDreamPhotoSource.class);
    private static final String IMAGE_PATH = "/home/lcm/blobImgListView.do";
    private final URI baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final long intervalMillis;
    private int consecutiveFailures;
    private int detailRequests;
    private int imageRequests;
    public int detailRequests() { return detailRequests; }
    public int imageRequests() { return imageRequests; }

    public SafeDreamPhotoSource(URI baseUrl) { this(baseUrl, 250); }
    public SafeDreamPhotoSource(URI baseUrl, long intervalMillis) {
        this.baseUrl = baseUrl;
        this.intervalMillis = intervalMillis;
    }

    public record Image(String sourceUrl, String dataUrl, String sha256) {}

    public record Photos(List<String> urls, String status, List<Image> images) {
        public Photos { urls = List.copyOf(urls); images = List.copyOf(images); }
        public Photos(List<String> urls, String status) { this(urls, status, List.of()); }
        public static Photos apiOnly(SourcePerson person) {
            return new Photos(person.photoUrl() == null ? List.of() : List.of(person.photoUrl()),
                    person.photoUrl() == null ? "UNCONFIRMED" : "API_FALLBACK");
        }
    }

    // region 사진 내용 자체를 동일한 전용 세션에서 수신
    public synchronized Photos fetch(SourcePerson person) {
        if (!person.sourceId().matches("[0-9]{1,30}")) throw new IllegalArgumentException("실종자 식별자를 확인하세요.");
        if (Thread.currentThread().isInterrupted()) return new Photos(List.of(), "UNCONFIRMED");
        // 사람마다 쿠키를 분리합니다. 다른 상세 화면이 사진 세션을 덮어쓰지 못하게 합니다.
        cookies.getCookieStore().removeAll();
        if (consecutiveFailures >= 3) return fallback(person);
        try {
            if (intervalMillis > 0) Thread.sleep(intervalMillis);
            URI detail = baseUrl.resolve("/home/lcm/lcmMssGet.do?gnbMenuCd=014000000000&lnbMenuCd=014001000000&rptDscd=2&msspsnIdntfccd=" + person.sourceId());
            detailRequests++;
            var response = client.send(HttpRequest.newBuilder(detail).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || !response.body().contains("fn_lcmMssImgChange")) {
                consecutiveFailures++;
                return fallback(person);
            }
            var urls = extract(response.body(), person.sourceId());
            var images = new ArrayList<Image>();
            for (String url : urls) {
                try {
                    if (intervalMillis > 0) Thread.sleep(intervalMillis);
                    images.add(downloadWithRetry(URI.create(url)));
                } catch (java.io.IOException e) {
                    // 사진 하나가 실패해도 이미 받은 정상 사진은 유지합니다.
                    log.warn("실종아동 사진 수신 실패: 식별자={}, 원인={}", person.sourceId(), e.getMessage());
                }
            }
            consecutiveFailures = 0;
            if (!images.isEmpty()) return new Photos(urls,
                    images.size() == urls.size() ? "DETAIL_LIST" : "PARTIAL", images);
            if (person.photoUrl() != null || !urls.isEmpty()) return fallback(person);
            return new Photos(List.of(), "NO_PHOTOS");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Photos(List.of(), "UNCONFIRMED");
        } catch (java.io.IOException | IllegalArgumentException e) {
            consecutiveFailures++;
            return fallback(person);
        }
    }

    private Photos fallback(SourcePerson person) {
        if (person.photoUrl() != null && !Thread.currentThread().isInterrupted()) {
            try {
                URI original = URI.create(person.photoUrl());
                if (!"https".equals(original.getScheme()) || !"www.safe182.go.kr".equals(original.getHost())
                        || !"/api/lcm/imgView.do".equals(original.getPath())
                        || !Objects.equals(original.getRawQuery(), "msspsnIdntfccd=" + person.sourceId()))
                    return new Photos(List.of(), "UNCONFIRMED");
                URI uri = baseUrl.resolve("/api/lcm/imgView.do?msspsnIdntfccd=" + person.sourceId());
                return new Photos(List.of(person.photoUrl()), "API_FALLBACK", List.of(download(uri)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException | IllegalArgumentException e) {
                // 확인되지 않은 외부 URL을 브라우저에 직접 표시하지 않습니다.
            }
        }
        return new Photos(List.of(), "UNCONFIRMED");
    }

    private Image downloadWithRetry(URI uri) throws java.io.IOException, InterruptedException {
        try {
            return download(uri);
        } catch (java.io.IOException first) {
            // 상세 페이지를 다시 열지 않고 동일한 아동 세션에서 한 번만 재시도합니다.
            Thread.sleep(Math.max(250, intervalMillis));
            return download(uri);
        }
    }

    private Image download(URI uri) throws java.io.IOException, InterruptedException {
        imageRequests++;
        var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() != 200) throw new java.io.IOException("사진 조회 실패");
            byte[] bytes = stream.readNBytes(2 * 1024 * 1024 + 1);
            if (bytes.length > 2 * 1024 * 1024) throw new java.io.IOException("사진 크기 제한 초과");
            boolean jpeg = bytes.length > 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8;
            boolean png = bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
            if (!jpeg && !png) throw new java.io.IOException("사진이 아닌 응답");
            // 일부 원본 JPEG는 JVM의 색상 변환에서 실패하므로, 변환하지 않고 헤더와 크기를 검증합니다.
            try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new java.io.IOException("읽을 수 없는 사진");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (reader.getWidth(0) < 1 || reader.getHeight(0) < 1)
                        throw new java.io.IOException("사진 크기 확인 실패");
                } finally { reader.dispose(); }
            }
            try {
                return new Image(uri.toString(), "data:image/" + (jpeg ? "jpeg" : "png") + ";base64,"
                        + Base64.getEncoder().encodeToString(bytes),
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
    // endregion

    // region HTML에 있는 같은 사람·같은 서버의 사진만 순서대로 추출
    List<String> extract(String html, String sourceId) throws java.io.IOException {
        var ordered = new TreeMap<Integer, String>();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (tag != HTML.Tag.IMG) return;
                Object src = attributes.getAttribute(HTML.Attribute.SRC);
                if (src == null) return;
                try {
                    URI uri = baseUrl.resolve(src.toString());
                    if (!Objects.equals(uri.getScheme(), baseUrl.getScheme())
                            || !Objects.equals(uri.getHost(), baseUrl.getHost()) || uri.getPort() != baseUrl.getPort()
                            || uri.getUserInfo() != null || !IMAGE_PATH.equals(uri.getPath())) return;
                    Map<String, String> query = new HashMap<>();
                    for (String pair : Objects.requireNonNullElse(uri.getRawQuery(), "").split("&")) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length == 2 && query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null) return;
                    }
                    String index = query.get("tknphotoFileIdx");
                    if (!sourceId.equals(query.get("p")) || index == null || !index.matches("[0-9]{1,6}")) return;
                    int number = Integer.parseInt(index);
                    // URL은 페이지에 나타난 순번만 사용하며 없는 번호를 추측하지 않습니다.
                    ordered.putIfAbsent(number, baseUrl.resolve(IMAGE_PATH + "?tknphotoFileIdx=" + number + "&p=" + sourceId).toString());
                } catch (IllegalArgumentException ignored) {
                    // 잘못된 URL 하나 때문에 다른 정상 사진이 누락되지 않도록 건너뜁니다.
                }
            }
        }, true);
        return List.copyOf(ordered.values());
    }
    // endregion
}
