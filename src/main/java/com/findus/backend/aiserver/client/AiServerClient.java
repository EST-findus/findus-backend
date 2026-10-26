package com.findus.backend.aiserver.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.findus.backend.aiserver.exception.AiServerException;
import com.findus.backend.aiserver.exception.AiServerException.Reason;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** ai_server_final의 POST /v1/hair를 호출하고 결과 PNG까지 받아옵니다. */
public class AiServerClient {
    private static final String RESULT_PATH = "/v1/results/";
    private static final int MAX_RESULT_BYTES = 20 * 1024 * 1024;
    private final URI baseUrl;
    private final RestClient client;

    public AiServerClient(URI baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(timeout);
        var builder = RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(factory);
        if (apiKey != null) builder.defaultHeader("X-API-Key", apiKey);
        this.client = builder.build();
    }

    /** info는 AI 서버가 남긴 단계별 정보(참조 사진 ref, 배율 scale 등)입니다. */
    public record HairResult(String resultId, double seconds, byte[] png, Map<String, Object> info) {}

    // region AI 서버 응답 형식 (app/schemas.py ResultOut)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultOut(@JsonProperty("result_id") String resultId, double seconds, List<ImageOut> images) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImageOut(String key, String url, Map<String, Object> info) {}

    // endregion

    /** 얼굴 사진 한 장에 머리 스타일을 입힙니다. 참조 사진은 AI 서버가 얼굴 폭으로 자동 선택합니다. */
    public HairResult hair(byte[] image, String sex, String style) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("image", new ByteArrayResource(image) {
            @Override public String getFilename() { return "face.png"; }
        });
        form.add("sex", sex);
        form.add("style", style);
        form.add("ref", "auto");
        try {
            ResultOut out = client.post().uri("/v1/hair").contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                    .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw failure(response.getStatusCode().value(), new String(
                                response.getBody().readNBytes(4096), StandardCharsets.UTF_8));
                    })
                    .body(ResultOut.class);
            if (out == null || out.resultId() == null || out.images() == null || out.images().size() != 1)
                throw new AiServerException(Reason.UNAVAILABLE, "AI 서버 응답이 올바르지 않습니다.");
            ImageOut output = out.images().get(0);
            return new HairResult(out.resultId(), out.seconds(), download(output.url()),
                    output.info() == null ? Map.of() : output.info());
        } catch (RestClientException e) {
            if (e.getCause() instanceof AiServerException failure) throw failure;
            throw new AiServerException(Reason.UNAVAILABLE, "AI 서버에 연결하지 못했습니다.", e);
        }
    }

    private byte[] download(String url) {
        // 응답에 들어 있는 주소라도 같은 AI 서버의 결과 경로만 따라갑니다.
        if (url == null || !url.startsWith(RESULT_PATH) || url.contains("..") || url.contains("//"))
            throw new AiServerException(Reason.UNAVAILABLE, "AI 서버 결과 주소가 올바르지 않습니다.");
        byte[] png = client.get().uri(baseUrl.resolve(url)).accept(MediaType.IMAGE_PNG).retrieve().body(byte[].class);
        if (png == null || png.length < 8 || png.length > MAX_RESULT_BYTES || png[0] != (byte) 0x89 || png[1] != 0x50)
            throw new AiServerException(Reason.UNAVAILABLE, "AI 서버 결과 이미지가 올바르지 않습니다.");
        return png;
    }

    private AiServerException failure(int status, String body) {
        return switch (status) {
            // 400은 얼굴 미검출처럼 사진 자체의 문제라 AI 서버 안내를 그대로 남깁니다.
            case 400 -> new AiServerException(Reason.BAD_INPUT, detail(body));
            case 503 -> new AiServerException(Reason.BUSY, "AI 서버가 다른 변환을 처리 중입니다. 잠시 후 다시 시도하세요.");
            default -> new AiServerException(Reason.UNAVAILABLE, "AI 서버 오류 (" + status + ")");
        };
    }

    private static String detail(String body) {
        try {
            var detail = new ObjectMapper().readTree(body).path("detail");
            if (detail.isString() && !detail.asString().isBlank()) return detail.asString();
        } catch (JacksonException ignored) {
            // JSON이 아니면 일반 안내로 대신합니다.
        }
        return "사진을 처리하지 못했습니다.";
    }
}
