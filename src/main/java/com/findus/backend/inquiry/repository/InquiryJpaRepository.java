package com.findus.backend.inquiry.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.inquiry.entity.Inquiry;
import com.findus.backend.inquiry.entity.InquiryStatus;
import com.findus.backend.inquiry.entity.InquiryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InquiryJpaRepository extends JpaRepository<Inquiry, UUID> {
	// 필터 값이 없으면 해당 조건을 적용하지 않습니다.
	@Query("select i from Inquiry i where i.deletedAt is null"
			+ " and (:status is null or i.status = :status) and (:type is null or i.type = :type)")
	Page<Inquiry> findActive(@Param("status") InquiryStatus status, @Param("type") InquiryType type, Pageable pageable);

	Optional<Inquiry> findByIdAndDeletedAtIsNull(UUID id);
}
