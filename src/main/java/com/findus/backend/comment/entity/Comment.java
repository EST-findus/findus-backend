package com.findus.backend.comment.entity;

import java.util.UUID;
import com.findus.backend.common.entity.BaseEntity;
import com.findus.backend.member.entity.Member;
import com.findus.backend.post.entity.Post;
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
@Table(name = "comments", schema = "findus")
public class Comment extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	// 반대 방향 목록을 만들지 않으며 API에는 엔티티 대신 DTO를 반환합니다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false, updatable = false)
	private Post post;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member author;

	@Column(nullable = false, length = 1000)
	private String content;

	public Comment(Post post, Member author, String content) {
		if (post == null || author == null) throw new IllegalArgumentException("게시글과 작성자가 필요합니다.");
		this.post = post;
		this.author = author;
		this.content = validContent(content);
	}

	public void update(String content) {
		if (isDeleted()) throw new IllegalStateException("삭제된 댓글은 수정할 수 없습니다.");
		this.content = validContent(content);
	}

	private static String validContent(String value) {
		if (value == null || value.isBlank() || value.length() > 1000) {
			throw new IllegalArgumentException("댓글은 공백만 사용할 수 없으며 1000자 이하여야 합니다.");
		}
		// 줄바꿈과 들여쓰기는 그대로 보관합니다.
		return value;
	}
}
