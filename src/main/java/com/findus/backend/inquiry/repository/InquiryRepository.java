package com.findus.backend.inquiry.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.inquiry.entity.Inquiry;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryRepository {
	Inquiry save(Inquiry inquiry);
	Page<Inquiry> findAll(InquiryStatus status, InquiryType type, Pageable pageable);
	Optional<Inquiry> findById(UUID id);
}
