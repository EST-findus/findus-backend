package com.findus.backend.post.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PostRepositoryImpl implements PostRepository {
	private final PostJpaRepository jpa;

	public PostRepositoryImpl(PostJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	@Transactional
	public Post save(Post post) { return jpa.saveAndFlush(post); }

	@Override
	public Optional<Post> findById(UUID id) { return jpa.findByIdAndDeletedAtIsNull(id); }

	@Override
	@Transactional
	public Optional<Post> findByIdForUpdate(UUID id) { return jpa.findActiveForUpdate(id); }

	@Override
	public Page<Post> findAll(Pageable pageable) { return jpa.findAllByDeletedAtIsNull(pageable); }
}
