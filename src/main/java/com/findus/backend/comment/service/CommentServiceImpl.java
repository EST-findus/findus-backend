package com.findus.backend.comment.service;

import java.util.Set;
import java.util.UUID;
import com.findus.backend.auth.exception.AuthException;
import com.findus.backend.comment.dto.CommentPageResponse;
import com.findus.backend.comment.dto.CommentResponse;
import com.findus.backend.comment.dto.CommentWriteRequest;
import com.findus.backend.comment.entity.Comment;
import com.findus.backend.comment.exception.CommentForbiddenException;
import com.findus.backend.comment.exception.CommentNotFoundException;
import com.findus.backend.comment.exception.CommentPageException;
import com.findus.backend.comment.repository.CommentRepository;
import com.findus.backend.member.entity.MemberStatus;
import com.findus.backend.member.repository.MemberRepository;
import com.findus.backend.post.entity.Post;
import com.findus.backend.post.exception.PostNotFoundException;
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
public class CommentServiceImpl implements CommentService {
	private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "id");
	private final CommentRepository comments;
	private final PostRepository posts;
	private final MemberRepository members;

	public CommentServiceImpl(CommentRepository comments, PostRepository posts, MemberRepository members) {
		this.comments = comments;
		this.posts = posts;
		this.members = members;
	}

	@Override
	@Transactional
	public CommentResponse create(UUID postId, UUID memberId, CommentWriteRequest request) {
		Post post = activePostForUpdate(postId);
		var member = members.findById(memberId).orElseThrow(AuthException::new);
		if (member.getStatus() != MemberStatus.ACTIVE) throw new AuthException();
		return CommentResponse.from(comments.save(new Comment(post, member, request.content())));
	}

	@Override
	public CommentPageResponse findAll(UUID postId, Pageable pageable) {
		posts.findById(postId).orElseThrow(PostNotFoundException::new);
		if (pageable.isUnpaged() || pageable.getPageSize() > 100 || pageable.getOffset() > Integer.MAX_VALUE) {
			throw new CommentPageException();
		}
		Sort sort = pageable.getSort();
		for (Sort.Order order : sort) {
			if (!SORT_FIELDS.contains(order.getProperty())) throw new CommentPageException();
		}
		if (sort.isUnsorted()) sort = Sort.by(Sort.Direction.ASC, "createdAt");
		// 작성 시간이 같아도 페이지 간 중복되지 않도록 UUID를 마지막 정렬 기준으로 사용합니다.
		if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
		return CommentPageResponse.from(comments.findAll(postId,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)).map(CommentResponse::from));
	}

	@Override
	@Transactional
	public CommentResponse update(UUID postId, UUID id, UUID memberId, CommentWriteRequest request) {
		Comment comment = ownedComment(postId, id, memberId);
		comment.update(request.content());
		comments.save(comment);
		return CommentResponse.from(comment);
	}

	@Override
	@Transactional
	public void delete(UUID postId, UUID id, UUID memberId) { ownedComment(postId, id, memberId).softDelete(); }

	private Post activePostForUpdate(UUID postId) {
		// 항상 게시글부터 잠가 게시글 삭제·댓글 변경의 순서를 일관되게 유지합니다.
		return posts.findByIdForUpdate(postId).orElseThrow(PostNotFoundException::new);
	}

	private Comment ownedComment(UUID postId, UUID id, UUID memberId) {
		activePostForUpdate(postId);
		Comment comment = comments.findByIdForUpdate(postId, id).orElseThrow(CommentNotFoundException::new);
		if (!comment.getAuthor().getId().equals(memberId)) throw new CommentForbiddenException();
		return comment;
	}
}
