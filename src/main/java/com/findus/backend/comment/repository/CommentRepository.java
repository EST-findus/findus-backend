package com.findus.backend.comment.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentRepository {
	Comment save(Comment comment);
	Page<Comment> findAll(UUID postId, Pageable pageable);
	Optional<Comment> findByIdForUpdate(UUID postId, UUID id);
}
