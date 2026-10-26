package com.findus.backend.post.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.post.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PostJpaRepository extends JpaRepository<Post, UUID> {
	// 작성자를 함께 조회하여 게시글마다 추가 조회가 발생하지 않게 합니다.
	@EntityGraph(attributePaths = "author")
	Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

	@EntityGraph(attributePaths = "author")
	Page<Post> findAllByDeletedAtIsNull(Pageable pageable);

	// 같은 글의 수정·삭제가 겹치면 순서대로 처리해 삭제 상태를 되돌리지 않게 합니다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :id and p.deletedAt is null")
	Optional<Post> findActiveForUpdate(@Param("id") UUID id);
}
