package com.findus.backend.comment.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.comment.entity.Comment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommentJpaRepository extends JpaRepository<Comment, UUID> {
	// 작성자를 함께 읽어 댓글마다 회원 조회 쿼리가 발생하지 않게 합니다.
	@EntityGraph(attributePaths = "author")
	@Query("select c from Comment c where c.post.id = :postId and c.post.deletedAt is null and c.deletedAt is null")
	Page<Comment> findActive(@Param("postId") UUID postId, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Comment c where c.id = :id and c.post.id = :postId and c.deletedAt is null")
	Optional<Comment> findActiveForUpdate(@Param("postId") UUID postId, @Param("id") UUID id);
}
