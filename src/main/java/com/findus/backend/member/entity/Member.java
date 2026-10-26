package com.findus.backend.member.entity;

import java.util.Locale;
import java.util.UUID;

import com.findus.backend.common.entity.BaseEntity;
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
@Table(name = "members", schema = "findus")
public class Member extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 254, updatable = false)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role = MemberRole.USER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberStatus status = MemberStatus.ACTIVE;

	/** passwordHash에는 이후 회원가입 서비스에서 해싱한 값만 전달합니다. */
	public Member(String email, String passwordHash, String name, String nickname) {
		this.email = normalizeEmail(email);
		this.passwordHash = requireText(passwordHash, 255, "비밀번호 해시");
		this.name = requireText(name, 100, "이름");
		this.nickname = requireText(nickname, 50, "닉네임");
	}

	/** null인 항목은 기존 값을 유지합니다. 비밀번호 해싱 자체는 이 엔티티에서 하지 않습니다. */
	public void updateMember(String nickname, String passwordHash) {
		requireNotDeleted();
		// 두 값의 검증이 끝난 뒤 반영하여, 검증 실패 시 일부 필드만 변경되지 않게 합니다.
		String nextNickname = nickname == null ? this.nickname : requireText(nickname, 50, "닉네임");
		String nextPasswordHash = passwordHash == null
				? this.passwordHash : requireText(passwordHash, 255, "비밀번호 해시");
		this.nickname = nextNickname;
		this.passwordHash = nextPasswordHash;
	}

	public void deactivate() {
		requireNotDeleted();
		this.status = MemberStatus.INACTIVE;
	}

	public void activate() {
		requireNotDeleted();
		this.status = MemberStatus.ACTIVE;
	}

	@Override
	public void softDelete() {
		super.softDelete();
		this.status = MemberStatus.INACTIVE;
	}

	public static String normalizeEmail(String email) {
		return requireText(email, 254, "이메일").toLowerCase(Locale.ROOT);
	}

	private void requireNotDeleted() {
		if (isDeleted()) {
			throw new IllegalStateException("삭제된 회원은 변경하거나 활성화할 수 없습니다.");
		}
	}

	private static String requireText(String value, int maxLength, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "은(는) 비어 있을 수 없습니다.");
		}
		String trimmed = value.strip();
		if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
			throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하여야 합니다.");
		}
		return trimmed;
	}
}
