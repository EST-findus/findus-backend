package com.findus.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {
	@Bean
	public OpenAPI findUsOpenApi() {
		return new OpenAPI()
				.info(new Info().title("FindUs API").version("v1")
						.description("회원·인증·게시글·댓글·실종자·관심아동 API를 확인하고 테스트하는 로컬 문서입니다. "
								+ "회원가입 → 보안 토큰 준비(GET /api/auth/csrf) → 로그인 → Authorize 순서로 진행하세요. "
								+ "Authorize에는 로그인 응답의 accessToken만 입력합니다. "
								+ "인증이 필요한 쓰기 요청에는 CSRF도 필요하며 Swagger가 쿠키에서 읽어 자동 전달합니다. "
								+ "MVP local 프로필의 실종자 동기화 실행·이력 조회는 회원가입·로그인·JWT·CSRF 없이 가능합니다. Refresh Token은 브라우저의 HttpOnly 쿠키로 관리합니다. 실제 DB 데이터가 변경될 수 있습니다."))
				.components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
						.description("로그인 응답의 accessToken만 입력하세요. Bearer 접두사는 자동으로 붙습니다.")));
	}
}
