package com.findus.backend.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostWriteRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.") @Schema(description = "제목: 공백만 사용 불가, 최대 200자", example = "첫 게시글") String title,
		@NotBlank(message = "본문을 입력해 주세요.")
		@Size(max = 10000, message = "본문은 10000자 이하여야 합니다.") @Schema(description = "본문: 공백만 사용 불가, 최대 10000자. 일반 텍스트로 사용합니다.", example = "Swagger에서 작성한 테스트 게시글입니다.") String content) {
	public PostWriteRequest {
		title = title == null ? null : title.strip();
	}
}
