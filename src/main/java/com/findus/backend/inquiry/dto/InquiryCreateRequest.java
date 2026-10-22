package com.findus.backend.inquiry.dto;

import com.findus.backend.inquiry.entity.InquiryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 프론트 InquiryForm이 보내는 본문을 그대로 받습니다. 문구는 lib/inquiry.ts의 validateInquiry와 같습니다. */
public record InquiryCreateRequest(
		@Schema(description = "문의 유형. 코드 또는 한글 라벨: BUG=오류 신고, FEATURE=기능 제안, CORRECTION=정보 정정 요청, ETC=기타", example = "오류 신고")
		@NotNull(message = "문의 유형을 골라 주세요.") InquiryType type,
		@Schema(description = "문의 내용: 앞뒤 공백 제거 후 10~1000자, 줄바꿈 보존", example = "검색 화면에서 지역 필터가 적용되지 않아요.")
		@NotNull(message = "내용을 10자 이상 적어 주세요.")
		@Size(min = 10, message = "내용을 10자 이상 적어 주세요.")
		@Size(max = 1000, message = "내용은 1000자까지 적을 수 있어요.") String message,
		@Schema(description = "답변 받을 이메일(선택). 입력하면 agree=true가 필요하며 답변 완료·종료 시 삭제", example = "name@example.com")
		@Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 주소를 다시 확인해 주세요.")
		@Size(max = 254, message = "이메일 주소를 다시 확인해 주세요.") String email,
		@Schema(description = "이메일 수집 동의 여부", example = "true") Boolean agree,
		@Schema(description = "문의를 보낸 화면 경로(선택). 200자를 넘으면 잘라서 저장", example = "/search") String page,
		@Schema(description = "자동 입력 방지용 숨김 칸. 사람은 비워 둡니다.", example = "") String website) {

	public InquiryCreateRequest {
		// 길이 검증이 앞뒤 공백을 뺀 값으로 이뤄지도록 검증 전에 정리합니다.
		if (message != null) message = message.strip();
		email = email == null || email.isBlank() ? null : email.strip();
		if (page != null) page = page.isBlank() ? null : page.substring(0, Math.min(page.length(), 200));
	}
}
