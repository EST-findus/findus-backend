package com.findus.backend.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostWriteRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 200, message = "제목은 200자 이하여야 합니다.") String title,
		@NotBlank(message = "본문을 입력해 주세요.")
		@Size(max = 10000, message = "본문은 10000자 이하여야 합니다.") String content) {
	public PostWriteRequest {
		title = title == null ? null : title.strip();
	}
}
