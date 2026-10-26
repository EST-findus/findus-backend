package com.findus.backend.common;

import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
		"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
		"springdoc.swagger-ui.csrf.enabled=true", "springdoc.swagger-ui.persist-authorization=false",
		"springdoc.swagger-ui.disable-swagger-default-url=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Swagger 문서와 기존 인증 규칙 통합 테스트")
class SwaggerTests {
	// region 개발 DB와 분리된 임시 환경
	@Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-bookworm")
			.withUsername("findus").withDatabaseName("findus");
	@Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine")
			.withExposedPorts(6379).withCommand("redis-server", "--requirepass", "swagger-test-redis");
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "swagger-test-redis");
	}
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	// endregion

    @Test
    void AI저장구조를_추가해도_기존_OpenAPI문서는_동일하다() throws Exception {
        var result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var doc=(tools.jackson.databind.node.ObjectNode)mapper.readTree(result.getResponse().getContentAsString());
        // 테스트 실행 주소만 제외하고 경로·요청·응답 스키마·설명 전체를 비교합니다.
        doc.remove("servers");
        // 이후 추가한 뉴스·문의·AI 이미지·AI 스타일 API는 기준 문서에 없으므로 제외하고 기존 문서만 비교합니다.
        var paths=(tools.jackson.databind.node.ObjectNode)doc.path("paths");
        paths.remove(paths.properties().stream().map(java.util.Map.Entry::getKey).filter(path -> path.contains("/news") || path.contains("/inquiries")
                || path.contains("/ai-image") || path.contains("/ai-styles")).toList());
        var schemas=(tools.jackson.databind.node.ObjectNode)doc.path("components").path("schemas");
        schemas.remove(schemas.properties().stream().map(java.util.Map.Entry::getKey).filter(name -> name.startsWith("News") || name.startsWith("Inquiry") || name.equals("JsonNode")
                || name.startsWith("MissingAiImageResponse") || name.equals("Style") || name.startsWith("AiStyle")).toList());
        // 목록·상세 응답에 추가한 대표 AI 사진 주소도 기준 문서에는 없으므로 제외합니다.
        if (schemas.path("MissingPersonResponse").path("properties") instanceof tools.jackson.databind.node.ObjectNode person)
            person.remove("aiPhotoUrl");
        if (doc.path("tags") instanceof tools.jackson.databind.node.ArrayNode tags)
            for (int i=tags.size()-1;i>=0;i--) {
                String name=tags.get(i).path("name").asString();
                if (name.startsWith("뉴스") || name.equals("문의") || name.startsWith("AI 이미지") || name.startsWith("AI 스타일")) tags.remove(i);
            }
        try (var baseline=getClass().getResourceAsStream("/contracts/openapi-before-ai-images.json")) {
            assertThat(baseline).isNotNull();
            var expected=mapper.readTree(baseline);
            // 컨트롤러가 늘면 태그 나열 순서만 바뀌므로 태그는 이름순으로 맞춘 뒤 내용을 비교합니다.
            sortTags(doc);sortTags(expected);
            assertThat(doc).isEqualTo(expected);
        }
    }
    private static void sortTags(tools.jackson.databind.JsonNode doc) {
        if (!(doc.path("tags") instanceof tools.jackson.databind.node.ArrayNode tags)) return;
        var sorted=new java.util.ArrayList<tools.jackson.databind.JsonNode>();
        tags.forEach(sorted::add);
        // 이름이 같은 태그(실종자)가 설명만 달리해 둘 있으므로 설명까지 기준으로 씁니다.
        sorted.sort(java.util.Comparator.<tools.jackson.databind.JsonNode,String>comparing(tag -> tag.path("name").asString())
                .thenComparing(tag -> tag.path("description").asString()));
        tags.removeAll();sorted.forEach(tags::add);
    }

	@Test
	@DisplayName("문의 태그로 API 5개가 문서에 나타난다")
	void 문의_API_5개를_문서화한다() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var paths = mapper.readTree(result.getResponse().getContentAsString()).path("paths");
		long count = paths.properties().stream().flatMap(path -> path.getValue().properties().stream())
				.filter(operation -> operation.getValue().path("tags").toString().contains("\"문의\"")).count();
		assertThat(count).isEqualTo(5);
	}

	// region 문서·화면·인증 안내
	@Test
	@DisplayName("로그인 없이 UI·문서·CSRF 설정을 읽을 수 있고 내부 인증 인자는 나타나지 않는다")
	void 문서와_UI를_공개하고_내부_인자를_숨긴다() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
		var config = mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk()).andReturn();
		assertThat(mapper.readTree(config.getResponse().getContentAsString()).path("persistAuthorization").asBoolean()).isFalse();
		// CSRF 처리는 설정 JSON이 아니라 Swagger 초기화 자바스크립트에 반영됩니다.
		var initializer = mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk()).andReturn();
		assertThat(initializer.getResponse().getContentAsString()).contains("requestInterceptor", "X-XSRF-TOKEN", "XSRF-TOKEN");
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var doc = mapper.readTree(result.getResponse().getContentAsString());
		assertThat(doc.at("/info/title").asText()).isEqualTo("FindUs API");
        assertThat(doc.at("/paths/~1api~1members~1me~1favorites/get/summary").asText()).isEqualTo("내 관심아동 목록");
        assertThat(doc.at("/paths/~1api~1members~1me~1favorites~1{missingPersonId}/put/security/0/bearerAuth").isArray()).isTrue();
        assertThat(doc.at("/paths/~1api~1members~1me~1favorites/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(doc.at("/paths/~1api~1members~1me~1favorites~1{missingPersonId}/delete/responses/204").isMissingNode()).isFalse();
		assertThat(doc.at("/paths/~1api~1posts~1{postId}~1comments/get/summary").asText()).isEqualTo("댓글 목록");
		assertThat(doc.at("/paths/~1api~1posts~1{postId}~1comments/post/security/0/bearerAuth").isArray()).isTrue();
		assertThat(doc.at("/components/schemas/CommentWriteRequest/properties/content/maxLength").asInt()).isEqualTo(1000);

		assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
		assertThat(doc.at("/paths/~1api~1members~1me/get/security/0/bearerAuth").isArray()).isTrue();
		assertThat(doc.at("/paths/~1api~1auth~1login/post/security").isMissingNode()).isTrue();
		for (String path : new String[]{"~1api~1members~1me/get", "~1api~1posts/post", "~1api~1auth~1csrf/get",
				"~1api~1auth~1refresh/post", "~1api~1auth~1logout/post"}) {
			assertThat(doc.at("/paths/" + path + "/parameters").isMissingNode())
					.as("로그인 정보와 쿠키는 수동 입력 항목으로 표시하지 않습니다: %s", path).isTrue();
		}
		assertThat(doc.at("/components/schemas/MemberSignupRequest/properties/password/writeOnly").asBoolean()).isTrue();
		assertThat(doc.at("/components/schemas/PostWriteRequest/properties/title/maxLength").asInt()).isEqualTo(200);
		assertThat(doc.at("/paths/~1api~1members/post/responses/201").isMissingNode()).isFalse();
		assertThat(doc.at("/paths/~1api~1posts/post/responses/201").isMissingNode()).isFalse();
		assertThat(doc.at("/paths/~1api~1posts~1{id}/delete/responses/204/content").isMissingNode()).isTrue();
		assertThat(doc.at("/paths/~1health/get/responses/200/content/text~1plain/schema/type").asText()).isEqualTo("string");
		mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("게시글 목록 문서에 page·size·sort와 기본값을 표시한다")
	void 페이지_조회_입력값을_표시한다() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var parameters = mapper.readTree(result.getResponse().getContentAsString()).at("/paths/~1api~1posts/get/parameters");
		var names = new java.util.ArrayList<String>();
		parameters.forEach(p -> names.add(p.path("name").asText()));
		assertThat(names).containsExactlyInAnyOrder("page", "size", "sort");
		parameters.forEach(p -> {
			if (p.path("name").asText().equals("size")) assertThat(p.at("/schema/default").asInt()).isEqualTo(10);
			if (p.path("name").asText().equals("page")) assertThat(p.at("/schema/default").asInt()).isZero();
		});
	}
	// endregion

	// region Swagger 사용 순서대로 실제 API 검증
	@Test
	@DisplayName("문서 공개가 JWT·CSRF 검증을 우회하지 않고 가입·로그인·게시글 CRUD가 동작한다")
	void 문서가_있어도_인증과_CSRF가_필요하다() throws Exception {
		String email = "swagger-" + UUID.randomUUID() + "@example.com";
		String password = "Swagger-test123!";
		mvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("email", email, "password", password, "name", "스웨거테스트", "nickname", "문서확인"))))
				.andExpect(status().isCreated());
		String loginBody = mapper.writeValueAsString(Map.of("email", email, "password", password));
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
				.andExpect(status().isForbidden());
		var prepared = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		Cookie csrfCookie = prepared.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrfCookie).isNotNull();
		String csrf = csrfCookie.getValue();
		var loggedIn = mvc.perform(post("/api/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", csrf)
				.contentType(MediaType.APPLICATION_JSON).content(loginBody)).andExpect(status().isOk()).andReturn();
		String auth = "Bearer " + mapper.readTree(loggedIn.getResponse().getContentAsString()).path("accessToken").asText();
		mvc.perform(get("/api/members/me").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
		String body = "{\"title\":\"문서 테스트\",\"content\":\"본문\"}";
		mvc.perform(post("/api/posts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		var created = mvc.perform(post("/api/posts").header("Authorization", auth).cookie(csrfCookie).header("X-XSRF-TOKEN", csrf)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
		String id = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
		mvc.perform(get("/api/posts/" + id)).andExpect(status().isOk());
		mvc.perform(get("/api/posts?page=0&size=10&sort=title,asc")).andExpect(status().isOk());
		mvc.perform(put("/api/posts/" + id).header("Authorization", auth).cookie(csrfCookie).header("X-XSRF-TOKEN", csrf)
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		mvc.perform(delete("/api/posts/" + id).header("Authorization", auth).cookie(csrfCookie).header("X-XSRF-TOKEN", csrf))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/posts/" + id)).andExpect(status().isNotFound());
	}
	// endregion
}
