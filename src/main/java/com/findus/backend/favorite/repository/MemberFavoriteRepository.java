package com.findus.backend.favorite.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.favorite.entity.MemberFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberFavoriteRepository {
    MemberFavorite save(MemberFavorite favorite);
    Optional<MemberFavorite> findIncludingDeleted(UUID memberId,UUID missingPersonId);
    Page<MemberFavorite> findActive(UUID memberId,Pageable page);
}
