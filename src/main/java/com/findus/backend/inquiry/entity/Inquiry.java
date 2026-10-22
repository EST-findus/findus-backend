package com.findus.backend.inquiry.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.findus.backend.common.entity.BaseEntity;
import com.findus.backend.inquiry.exception.InquiryException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inquiries", schema = "findus")
public class Inquiry extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	// 회원 정보는 응답에 쓰지 않으므로 ID만 보관합니다. 탈퇴 시 DB가 NULL로 바꿉니다.
	@Column(name = "member_id", updatable = false)
	private UUID memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private InquiryType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryStatus status = InquiryStatus.RECEIVED;

	@Column(nullable = false, length = 1000, updatable = false)
	private String message;

	@Column(length = 254)
	private String email;

	@Column(name = "email_agreed_at", updatable = false)
	private LocalDateTime emailAgreedAt;

	@Column(length = 200, updatable = false)
	private String page;

	@Column(name = "admin_memo", length = 2000)
	private String adminMemo;

	@Column(name = "answered_at")
	private LocalDateTime answeredAt;

	/** 요청 검증을 통과한 값만 들어오지만, 이메일 동의 없는 저장은 엔티티에서도 막습니다. */
	public Inquiry(UUID memberId, InquiryType type, String message, String email, boolean emailAgreed, String page) {
		if (type == null || message == null || message.length() < 10 || message.length() > 1000) {
			throw new IllegalArgumentException("문의 유형과 10~1000자 본문이 필요합니다.");
		}
		if (email != null && !emailAgreed) throw new IllegalArgumentException("이메일 수집 동의가 필요합니다.");
		this.memberId = memberId;
		this.type = type;
		this.message = message;
		this.email = email;
		this.emailAgreedAt = email == null ? null : LocalDateTime.now(ZoneOffset.UTC);
		this.page = page;
	}

	/** 답변 완료·종료가 되면 안내대로 이메일을 바로 지웁니다. */
	public void update(InquiryStatus next, String adminMemo) {
		if (isDeleted()) throw InquiryException.notFound();
		if (next.ordinal() < status.ordinal()) throw InquiryException.statusConflict();
		this.status = next;
		this.adminMemo = adminMemo;
		if (next == InquiryStatus.ANSWERED || next == InquiryStatus.CLOSED) {
			this.email = null;
			if (answeredAt == null) answeredAt = LocalDateTime.now(ZoneOffset.UTC);
		}
	}
}
