package com.findus.backend.comment.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CommentRepositoryImpl implements CommentRepository {
	private final CommentJpaRepository jpa;
	public CommentRepositoryImpl(CommentJpaRepository jpa) { this.jpa = jpa; }

	@Override
	@Transactional
	public Comment save(Comment comment) { return jpa.saveAndFlush(comment); }

	@Override
	public Page<Comment> findAll(UUID postId, Pageable pageable) { return jpa.findActive(postId, pageable); }

	@Override
	@Transactional
	public Optional<Comment> findByIdForUpdate(UUID postId, UUID id) { return jpa.findActiveForUpdate(postId, id); }
}
