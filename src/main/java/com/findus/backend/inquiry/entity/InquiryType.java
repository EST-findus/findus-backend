package com.findus.backend.inquiry.entity;

import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonCreator;

/** 프론트(lib/inquiry.ts)의 한글 라벨과 코드 모두 받고, 저장·응답은 코드로 합니다. */
public enum InquiryType {
	BUG("오류 신고"), FEATURE("기능 제안"), CORRECTION("정보 정정 요청"), ETC("기타");

	private final String label;
	InquiryType(String label) { this.label = label; }
	public String label() { return label; }

	/** 모르는 값은 null로 두어 "문의 유형을 골라 주세요." 검증 오류가 되게 합니다. */
	@JsonCreator
	public static InquiryType from(String value) {
		return Arrays.stream(values()).filter(type -> type.name().equals(value) || type.label.equals(value))
				.findFirst().orElse(null);
	}
}
