package com.findus.backend.common.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 생성·수정·소프트 삭제 시간을 공통으로 관리합니다. 시간은 UTC 기준입니다. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	/** 반복 호출해도 최초 삭제 시간을 유지하며, DB 행을 물리적으로 삭제하지 않습니다. */
	public void softDelete() {
		if (!isDeleted()) {
			this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
		}
	}

    /** 복원이 필요한 하위 도메인에서만 사용합니다. 일반 공개 수정 기능으로 노출하지 않습니다. */
    protected void restoreDeletedAt() { this.deletedAt = null; }

	public boolean isDeleted() {
		return deletedAt != null;
	}
}
