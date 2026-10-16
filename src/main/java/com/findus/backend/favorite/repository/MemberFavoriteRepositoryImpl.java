package com.findus.backend.favorite.repository;

import java.util.Optional;
import java.util.UUID;
import com.findus.backend.favorite.entity.MemberFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly=true)
public class MemberFavoriteRepositoryImpl implements MemberFavoriteRepository {
    private final MemberFavoriteJpaRepository jpa;
    public MemberFavoriteRepositoryImpl(MemberFavoriteJpaRepository jpa) { this.jpa=jpa; }
    @Override @Transactional
    public MemberFavorite save(MemberFavorite favorite) { return jpa.saveAndFlush(favorite); }
    @Override
    public Optional<MemberFavorite> findIncludingDeleted(UUID memberId,UUID missingPersonId) {
        return jpa.findByMemberIdAndMissingPersonId(memberId,missingPersonId);
    }
    @Override
    public Page<MemberFavorite> findActive(UUID memberId,Pageable page) {
        return jpa.findAllByMemberIdAndDeletedAtIsNull(memberId,page);
    }
}
