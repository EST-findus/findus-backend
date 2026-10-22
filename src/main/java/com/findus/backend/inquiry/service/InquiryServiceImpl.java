package com.findus.backend.inquiry.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.findus.backend.common.exception.ApiErrorResponse;
import com.findus.backend.inquiry.dto.InquiryCreateRequest;
import com.findus.backend.inquiry.dto.InquiryCreatedResponse;
import com.findus.backend.inquiry.dto.InquiryPageResponse;
import com.findus.backend.inquiry.dto.InquiryResponse;
import com.findus.backend.inquiry.dto.InquiryUpdateRequest;
import com.findus.backend.inquiry.entity.Inquiry;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import com.findus.backend.inquiry.exception.InquiryException;
import com.findus.backend.inquiry.repository.InquiryRateLimitRepository;
import com.findus.backend.inquiry.repository.InquiryRepository;
import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService {
	// 프론트 validateInquiry가 검사하는 순서와 같게 첫 오류를 고릅니다.
	private static final List<String> FIELD_ORDER = List.of("type", "message", "email", "agree");
	private final InquiryRepository inquiries;
	private final InquiryRateLimitRepository rateLimits;
	private final Validator validator;

	public InquiryServiceImpl(InquiryRepository inquiries, InquiryRateLimitRepository rateLimits, Validator validator) {
		this.inquiries = inquiries;
		this.rateLimits = rateLimits;
		this.validator = validator;
	}

	@Override
	@Transactional
	public InquiryCreatedResponse create(UUID memberId, String ip, InquiryCreateRequest request) {
		if (!rateLimits.tryAcquire(ip)) throw InquiryException.tooManyRequests();
		// 자동 입력은 검증·저장 없이 성공처럼 응답해 차단 여부를 알려주지 않습니다.
		if (request.website() != null && !request.website().isEmpty()) {
			return new InquiryCreatedResponse(UUID.randomUUID(), LocalDateTime.now(ZoneOffset.UTC));
		}
		validate(request);
		Inquiry saved = inquiries.save(new Inquiry(memberId, request.type(), request.message(), request.email(),
				Boolean.TRUE.equals(request.agree()), request.page()));
		return new InquiryCreatedResponse(saved.getId(), saved.getCreatedAt());
	}

	@Override
	public InquiryPageResponse findAll(InquiryStatus status, InquiryType type, Pageable pageable) {
		if (pageable.isUnpaged() || pageable.getPageSize() > 100 || pageable.getOffset() > Integer.MAX_VALUE) {
			throw InquiryException.invalidPage();
		}
		Sort sort = pageable.getSort();
		for (Sort.Order order : sort) {
			if (!order.getProperty().equals("createdAt")) throw InquiryException.invalidPage();
		}
		if (sort.isUnsorted()) sort = Sort.by(Sort.Direction.DESC, "createdAt");
		// 접수 시간이 같아도 페이지 간 중복되지 않도록 UUID를 마지막 정렬 기준으로 사용합니다.
		sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
		return InquiryPageResponse.from(inquiries.findAll(status, type,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)).map(InquiryResponse::from));
	}

	@Override
	public InquiryResponse findById(UUID id) { return InquiryResponse.from(active(id)); }

	@Override
	@Transactional
	public InquiryResponse update(UUID id, InquiryUpdateRequest request) {
		Inquiry inquiry = active(id);
		inquiry.update(request.status(), request.adminMemo());
		return InquiryResponse.from(inquiries.save(inquiry));
	}

	@Override
	@Transactional
	public void delete(UUID id) { active(id).softDelete(); }

	private Inquiry active(UUID id) { return inquiries.findById(id).orElseThrow(InquiryException::notFound); }

	private void validate(InquiryCreateRequest request) {
		var errors = new ArrayList<ApiErrorResponse.FieldError>();
		validator.validate(request).forEach(violation -> errors.add(
				new ApiErrorResponse.FieldError(violation.getPropertyPath().toString(), violation.getMessage())));
		if (request.email() != null && !Boolean.TRUE.equals(request.agree())) {
			errors.add(new ApiErrorResponse.FieldError("agree", "답변을 받으려면 이메일 수집에 동의해 주세요."));
		}
		if (errors.isEmpty()) return;
		errors.sort(Comparator.comparingInt(error -> FIELD_ORDER.indexOf(error.field())));
		throw InquiryException.invalid(errors);
	}
}
