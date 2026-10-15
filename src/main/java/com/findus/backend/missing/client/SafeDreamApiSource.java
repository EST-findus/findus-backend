package com.findus.backend.missing.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.findus.backend.missing.config.MissingProperties;
import com.findus.backend.missing.dto.SourcePerson;
import com.findus.backend.missing.exception.MissingSyncException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SafeDreamApiSource implements MissingPersonSource {
    private final MissingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public SafeDreamApiSource(MissingProperties properties, ObjectMapper mapper) {
        this.properties = properties; this.mapper = mapper;
    }
    @Override
    public List<SourcePerson> fetchAll() {
        if (properties.esntlId().isBlank() || properties.authKey().isBlank())
            throw new MissingSyncException("안전Dream 인증정보를 설정하세요.");
        var result = new ArrayList<SourcePerson>();
        var identifiers = new HashSet<String>();
        int total = -1;
        for (int page = 1; page <= properties.maxPages(); page++) {
            JsonNode response = request(page);
            if (!"00".equals(response.path("result").asText()))
                throw new MissingSyncException("안전Dream 인증 또는 호출 제한 오류입니다.");
            int nextTotal = response.path("totalCount").asInt(-1);
            if (nextTotal <= 0 || nextTotal > properties.maxPages() * 100 || (total != -1 && total != nextTotal))
                throw new MissingSyncException("전체 건수가 비어 있거나 수집 중 변경되었습니다. 기존 DB를 유지합니다.");
            total = nextTotal;
            JsonNode rows = response.path("list");
            int expected = Math.min(100, total - result.size());
            if (!rows.isArray() || rows.size() != expected)
                throw new MissingSyncException("페이지 응답이 누락되었습니다. 기존 DB를 유지합니다.");
            for (JsonNode row : rows) {
                SourcePerson person = convert(row);
                if (!identifiers.add(person.sourceId())) throw new MissingSyncException("중복 식별자가 수신되었습니다.");
                result.add(person);
            }
            if (result.size() == total) return List.copyOf(result);
        }
        throw new MissingSyncException("수집 페이지 제한을 초과했습니다.");
    }
    private JsonNode request(int page) {
        String body = "esntlId=" + encode(properties.esntlId()) + "&authKey=" + encode(properties.authKey())
                + "&rowSize=100&page=" + page;
        HttpRequest request = HttpRequest.newBuilder(properties.baseUrl().resolve("/api/lcm/findChildList.do"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    if (attempt == 2) throw new MissingSyncException("안전Dream 서버에 접속할 수 없습니다.");
                } else {
                    if (response.statusCode() != 200) throw new MissingSyncException("안전Dream HTTP 요청이 거절되었습니다.");
                    try { return mapper.readTree(response.body()); }
                    catch (RuntimeException e) { throw new MissingSyncException("안전Dream 응답 형식이 올바르지 않습니다."); }
                }
            } catch (java.io.IOException e) {
                if (attempt == 2) throw new MissingSyncException("안전Dream 연결 시간이 초과되었거나 통신에 실패했습니다.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new MissingSyncException("수집이 중단되었습니다.");
            }
            try { Thread.sleep((attempt + 1) * 1000L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new MissingSyncException("수집이 중단되었습니다."); }
        }
        throw new MissingSyncException("수집에 실패했습니다.");
    }
    private SourcePerson convert(JsonNode row) {
        String id = row.path("msspsnIdntfccd").asText("");
        String name = row.path("nm").asText("");
        String category = row.path("writngTrgetDscd").asText("");
        if (!id.matches("[0-9]{1,30}") || name.isBlank() || name.length() > 100 || !category.matches("[0-9]{3}"))
            throw new MissingSyncException("필수 실종자 정보가 누락되었거나 형식이 잘못되었습니다.");
        // 응답의 사진 내용은 DB에 복제하지 않고 공식 원본 조회 주소만 저장합니다.
        String photo = row.path("tknphotoFile").asText("").isBlank() ? null
                : "https://www.safe182.go.kr/api/lcm/imgView.do?msspsnIdntfccd=" + id;
        return new SourcePerson(id,name,category,text(row,"sexdstnDscd"),text(row,"occrde"),
                age(row,"age"),age(row,"ageNow"),text(row,"occrAdres"),
                text(row,"alldressingDscd"),text(row,"etcSpfeatr"),photo);
    }
    private static String text(JsonNode node, String key) {
        String value=node.path(key).asText(""); return value.isBlank() ? null : value;
    }
    private static Integer age(JsonNode node, String key) {
        String value=node.path(key).asText("");
        if (value.isBlank() || value.equals("불상")) return null;
        try { int age=Integer.parseInt(value); return age >= 0 && age <= 200 ? age : null; }
        catch (NumberFormatException e) { return null; }
    }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
