package com.findus.backend.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentWriteRequest(
		@Schema(description = "댓글: 공백만 사용 불가, 최대 1000자. 일반 텍스트로 사용합니다.", example = "도움이 되는 정보 감사합니다.")
		@NotBlank(message = "댓글을 입력해 주세요.")
		@Size(max = 1000, message = "댓글은 1000자 이하여야 합니다.") String content) {}
