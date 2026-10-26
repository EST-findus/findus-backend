package com.findus.backend.post.service;

import java.util.UUID;
import com.findus.backend.post.dto.PostPageResponse;
import com.findus.backend.post.dto.PostResponse;
import com.findus.backend.post.dto.PostWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;

public interface PostService {
	PostResponse create(@NotNull UUID memberId, @NotNull @Valid PostWriteRequest request);
	PostResponse findById(@NotNull UUID id);
	PostPageResponse findAll(@NotNull Pageable pageable);
	PostResponse update(@NotNull UUID id, @NotNull UUID memberId, @NotNull @Valid PostWriteRequest request);
	void delete(@NotNull UUID id, @NotNull UUID memberId);
}
