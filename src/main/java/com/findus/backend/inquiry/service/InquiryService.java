package com.findus.backend.inquiry.service;

import java.util.UUID;
import com.findus.backend.inquiry.dto.InquiryCreateRequest;
import com.findus.backend.inquiry.dto.InquiryCreatedResponse;
import com.findus.backend.inquiry.dto.InquiryPageResponse;
import com.findus.backend.inquiry.dto.InquiryResponse;
import com.findus.backend.inquiry.dto.InquiryUpdateRequest;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;

public interface InquiryService {
	/** 허니팟을 먼저 확인해야 하므로 요청 검증은 메서드 안에서 합니다. memberId는 비회원이면 null입니다. */
	InquiryCreatedResponse create(UUID memberId, @NotNull String ip, @NotNull InquiryCreateRequest request);
	InquiryPageResponse findAll(InquiryStatus status, InquiryType type, @NotNull Pageable pageable);
	InquiryResponse findById(@NotNull UUID id);
	InquiryResponse update(@NotNull UUID id, @NotNull @Valid InquiryUpdateRequest request);
	void delete(@NotNull UUID id);
}
