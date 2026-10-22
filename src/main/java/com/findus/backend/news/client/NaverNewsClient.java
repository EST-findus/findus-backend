package com.findus.backend.news.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.findus.backend.news.config.NewsProperties;
import com.findus.backend.news.exception.NewsException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 네이버 뉴스 검색 결과를 요청 중에만 전달합니다. 결과는 저장하거나 로그에 남기지 않습니다. */
@Component
@EnableConfigurationProperties(NewsProperties.class)
public class NaverNewsClient {
    private static final URI HUB=URI.create("https://naverapihub.apigw.ntruss.com/search/v1/news");
    private static final URI DEVELOPERS=URI.create("https://openapi.naver.com/v1/search/news.json");
    private final NewsProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public NaverNewsClient(NewsProperties properties,ObjectMapper mapper) { this.properties=properties;this.mapper=mapper; }

    public JsonNode search(String query,int start,int display,String sort) {
        if (!properties.configured()) throw new NewsException(HttpStatus.SERVICE_UNAVAILABLE,"NAVER_NOT_CONFIGURED",
                ".env의 NAVER_CLIENT_ID와 NAVER_CLIENT_SECRET을 입력한 뒤 서버를 재시작해 주세요.");
        boolean hub=properties.provider().equals("hub");
        URI endpoint=properties.endpoint()!=null ? properties.endpoint() : hub ? HUB : DEVELOPERS;
        String params="query="+URLEncoder.encode(query,StandardCharsets.UTF_8)+"&display="+display+"&start="+start+"&sort="+sort+(hub ? "&format=json" : "");
        HttpResponse<String> response;
        try {
            var request=HttpRequest.newBuilder(URI.create(endpoint+"?"+params)).timeout(Duration.ofSeconds(15))
                    .header(hub ? "X-NCP-APIGW-API-KEY-ID" : "X-Naver-Client-Id",properties.clientId())
                    .header(hub ? "X-NCP-APIGW-API-KEY" : "X-Naver-Client-Secret",properties.clientSecret())
                    .header("Accept","application/json").GET().build();
            response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new NewsException(HttpStatus.SERVICE_UNAVAILABLE,"NAVER_NOT_CONFIGURED","네이버 인증키 형식이 올바르지 않습니다. 줄바꿈 없이 입력해 주세요.");
        } catch (HttpTimeoutException e) {
            throw new NewsException(HttpStatus.GATEWAY_TIMEOUT,"NAVER_TIMEOUT","네이버 API 응답 시간이 초과되었습니다.");
        } catch (IOException e) {
            throw new NewsException(HttpStatus.BAD_GATEWAY,"NAVER_UNAVAILABLE","네이버 API에 연결하지 못했습니다. 네트워크 설정을 확인해 주세요.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NewsException(HttpStatus.BAD_GATEWAY,"NAVER_UNAVAILABLE","네이버 API 요청이 중단되었습니다.");
        }
        if (response.statusCode()!=200) throw upstreamError(response);
        JsonNode body;
        try { body=mapper.readTree(response.body()); }
        catch (JacksonException e) { throw invalid("네이버 API가 JSON이 아닌 응답을 반환했습니다."); }
        if (body==null || !body.isObject() || !body.path("items").isArray()) throw invalid("네이버 API 응답 형식을 확인할 수 없습니다.");
        for (JsonNode item:body.path("items")) if (!item.isObject()) throw invalid("네이버 API 검색 항목 형식이 올바르지 않습니다.");
        return body;
    }

    private NewsException upstreamError(HttpResponse<String> response) {
        int status=response.statusCode();
        String code="";
        try {
            JsonNode error=mapper.readTree(response.body());
            String raw=error.path("errorCode").asString(error.path("error").path("errorCode").asString(""));
            // 원본 오류 문구는 그대로 노출하지 않고 짧은 코드만 전달합니다.
            if (raw.matches("[A-Za-z0-9_-]{1,30}")) code=" / 코드 "+raw;
        } catch (JacksonException ignored) {}
        String help=switch (status) {
            case 401 -> "인증키와 API 발급처 설정을 확인해 주세요.";
            case 403 -> "뉴스 검색 API 권한과 호출 제한 설정을 확인해 주세요.";
            case 429 -> "호출 한도를 초과했습니다. 나중에 다시 검색해 주세요.";
            default -> "네이버 설정 또는 요청 조건을 확인해 주세요.";
        };
        return new NewsException(status==429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
                status==429 ? "NAVER_RATE_LIMITED" : "NAVER_ERROR","네이버 API HTTP "+status+code+". "+help);
    }
    private static NewsException invalid(String message) { return new NewsException(HttpStatus.BAD_GATEWAY,"NAVER_INVALID_RESPONSE",message); }
}
