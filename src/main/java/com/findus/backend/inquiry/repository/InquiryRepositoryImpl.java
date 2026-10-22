package com.findus.backend.inquiry.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.inquiry.entity.Inquiry;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class InquiryRepositoryImpl implements InquiryRepository {
	private final InquiryJpaRepository jpa;
	public InquiryRepositoryImpl(InquiryJpaRepository jpa) { this.jpa = jpa; }

	@Override
	@Transactional
	public Inquiry save(Inquiry inquiry) { return jpa.saveAndFlush(inquiry); }

	@Override
	public Page<Inquiry> findAll(InquiryStatus status, InquiryType type, Pageable pageable) {
		return jpa.findActive(status, type, pageable);
	}

	@Override
	public Optional<Inquiry> findById(UUID id) { return jpa.findByIdAndDeletedAtIsNull(id); }
}
