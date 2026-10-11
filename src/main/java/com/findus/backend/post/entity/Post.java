package com.findus.backend.post.entity;

import java.util.UUID;
import com.findus.backend.common.entity.BaseEntity;
import com.findus.backend.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "posts", schema = "findus")
public class Post extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member author;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	public Post(Member author, String title, String content) {
		if (author == null) throw new IllegalArgumentException("작성자가 필요합니다.");
		this.author = author;
		this.title = validTitle(title);
		this.content = validContent(content);
	}

	public void update(String title, String content) {
		if (isDeleted()) throw new IllegalStateException("삭제된 게시글은 수정할 수 없습니다.");
		String nextTitle = validTitle(title);
		String nextContent = validContent(content);
		this.title = nextTitle;
		this.content = nextContent;
	}

	private static String validTitle(String value) {
		if (value == null || value.isBlank() || value.strip().length() > 200) {
			throw new IllegalArgumentException("제목은 1~200자로 입력해 주세요.");
		}
		return value.strip();
	}

	private static String validContent(String value) {
		if (value == null || value.isBlank() || value.length() > 10000) {
			throw new IllegalArgumentException("본문은 1~10000자로 입력해 주세요.");
		}
		// 본문의 줄바꿈과 들여쓰기는 입력한 그대로 보관합니다.
		return value;
	}
}
