package com.findus.backend.post.service;

import java.util.Set;
import java.util.UUID;
import com.findus.backend.auth.exception.AuthException;
import com.findus.backend.member.entity.MemberStatus;
import com.findus.backend.member.repository.MemberRepository;
import com.findus.backend.post.dto.PostPageResponse;
import com.findus.backend.post.dto.PostResponse;
import com.findus.backend.post.dto.PostSummaryResponse;
import com.findus.backend.post.dto.PostWriteRequest;
import com.findus.backend.post.entity.Post;
import com.findus.backend.post.exception.PostForbiddenException;
import com.findus.backend.post.exception.PostNotFoundException;
import com.findus.backend.post.exception.PostPageException;
import com.findus.backend.post.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly = true)
public class PostServiceImpl implements PostService {
	private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "title", "id");
	private final PostRepository posts;
	private final MemberRepository members;

	public PostServiceImpl(PostRepository posts, MemberRepository members) {
		this.posts = posts;
		this.members = members;
	}

	@Override
	@Transactional
	public PostResponse create(UUID memberId, PostWriteRequest request) {
		var member = members.findById(memberId).orElseThrow(AuthException::new);
		if (member.getStatus() != MemberStatus.ACTIVE) throw new AuthException();
		return PostResponse.from(posts.save(new Post(member, request.title(), request.content())));
	}

	@Override
	public PostResponse findById(UUID id) {
		return PostResponse.from(posts.findById(id).orElseThrow(PostNotFoundException::new));
	}

	@Override
	public PostPageResponse findAll(Pageable pageable) {
		if (pageable.isUnpaged() || pageable.getPageSize() > 100 || pageable.getOffset() > Integer.MAX_VALUE) {
			throw new PostPageException();
		}
		Sort sort = pageable.getSort();
		for (Sort.Order order : sort) {
			if (!SORT_FIELDS.contains(order.getProperty())) throw new PostPageException();
		}
		if (sort.isUnsorted()) sort = Sort.by(Sort.Direction.DESC, "createdAt");
		// 같은 작성 시간을 가진 글도 페이지마다 순서가 바뀌지 않도록 UUID를 마지막 기준으로 사용합니다.
		if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
		var page = posts.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));
		return PostPageResponse.from(page.map(PostSummaryResponse::from));
	}

	@Override
	@Transactional
	public PostResponse update(UUID id, UUID memberId, PostWriteRequest request) {
		Post post = ownedPost(id, memberId);
		post.update(request.title(), request.content());
		// Auditing의 수정 시간이 응답에도 반영되도록 변경 내용을 즉시 저장합니다.
		posts.save(post);
		return PostResponse.from(post);
	}

	@Override
	@Transactional
	public void delete(UUID id, UUID memberId) {
		ownedPost(id, memberId).softDelete();
	}

	private Post ownedPost(UUID id, UUID memberId) {
		Post post = posts.findByIdForUpdate(id).orElseThrow(PostNotFoundException::new);
		if (!post.getAuthor().getId().equals(memberId)) throw new PostForbiddenException();
		return post;
	}
}
