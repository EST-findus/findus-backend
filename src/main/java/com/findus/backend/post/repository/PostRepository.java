package com.findus.backend.post.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostRepository {
	Post save(Post post);
	Optional<Post> findById(UUID id);
	Optional<Post> findByIdForUpdate(UUID id);
	Page<Post> findAll(Pageable pageable);
}
