package com.findus.backend.comment.service;

import java.util.UUID;
import com.findus.backend.comment.dto.CommentPageResponse;
import com.findus.backend.comment.dto.CommentResponse;
import com.findus.backend.comment.dto.CommentWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;

public interface CommentService {
	CommentResponse create(@NotNull UUID postId, @NotNull UUID memberId, @NotNull @Valid CommentWriteRequest request);
	CommentPageResponse findAll(@NotNull UUID postId, @NotNull Pageable pageable);
	CommentResponse update(@NotNull UUID postId, @NotNull UUID id, @NotNull UUID memberId, @NotNull @Valid CommentWriteRequest request);
	void delete(@NotNull UUID postId, @NotNull UUID id, @NotNull UUID memberId);
}
