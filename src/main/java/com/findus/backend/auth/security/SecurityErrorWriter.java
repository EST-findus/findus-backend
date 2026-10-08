package com.findus.backend.auth.security;

import java.io.IOException;

import com.findus.backend.common.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorWriter {
	private final ObjectMapper mapper;
	public SecurityErrorWriter(ObjectMapper mapper) { this.mapper = mapper; }

	public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		if (status == 401) { response.setHeader("WWW-Authenticate", "Bearer"); }
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write(mapper.writeValueAsString(ApiErrorResponse.of(code, message)));
	}
}
