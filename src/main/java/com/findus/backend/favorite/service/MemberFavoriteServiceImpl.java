package com.findus.backend.favorite.service;

import java.util.Set;
import java.util.UUID;
import com.findus.backend.auth.exception.AuthException;
import com.findus.backend.favorite.dto.FavoriteResponse;
import com.findus.backend.favorite.dto.FavoritePageResponse;
import com.findus.backend.favorite.entity.MemberFavorite;
import com.findus.backend.favorite.exception.FavoriteNotFoundException;
import com.findus.backend.favorite.exception.FavoritePageException;
import com.findus.backend.favorite.exception.FavoriteTargetException;
import com.findus.backend.favorite.repository.MemberFavoriteRepository;
import com.findus.backend.member.entity.Member;
import com.findus.backend.member.entity.MemberStatus;
import com.findus.backend.member.repository.MemberRepository;
import com.findus.backend.missing.repository.MissingPersonRepository;
import com.findus.backend.missing.service.MissingResponseAssembler;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional(readOnly=true)
public class MemberFavoriteServiceImpl implements MemberFavoriteService {
    private static final Set<String> SORT_FIELDS=Set.of("createdAt","id");
    private final MemberFavoriteRepository favorites;
    private final MemberRepository members;
    private final MissingPersonRepository missing;
    private final MissingResponseAssembler assembler;
    public MemberFavoriteServiceImpl(MemberFavoriteRepository favorites,MemberRepository members,MissingPersonRepository missing,MissingResponseAssembler assembler) {
        this.favorites=favorites;this.members=members;this.missing=missing;this.assembler=assembler;
    }
    @Override
    @Transactional
    public FavoriteResponse register(UUID memberId,UUID missingPersonId) {
        // 같은 회원의 관심 변경은 직렬화하여 최초 동시 등록도 한 행으로 처리합니다.
        Member member=activeMember(memberId,true);
        // 원본의 공개 상태·분류가 배치에서 변경되는 동안 등록되지 않도록 읽기 잠금을 유지합니다.
        var person=missing.findForFavorite(missingPersonId).orElseThrow(FavoriteNotFoundException::new);
        if (!person.isSourceAvailable()) throw new FavoriteNotFoundException();
        if (!"010".equals(person.getCategory())) throw new FavoriteTargetException();
        var favorite=favorites.findIncludingDeleted(memberId,missingPersonId)
                .orElseGet(() -> new MemberFavorite(member,person));
        if (favorite.isDeleted()) favorite.reactivate();
        return FavoriteResponse.from(favorites.save(favorite),assembler.from(person));
    }
    @Override
    @Transactional
    public void cancel(UUID memberId,UUID missingPersonId) {
        activeMember(memberId,true);
        // 공개 중단·분류 변경 이후에도 본인의 관심은 취소할 수 있습니다. 반복 취소는 204입니다.
        favorites.findIncludingDeleted(memberId,missingPersonId).ifPresent(MemberFavorite::softDelete);
    }
    @Override
    public FavoritePageResponse findMine(UUID memberId,Pageable pageable) {
        activeMember(memberId,false);
        if (pageable.isUnpaged() || pageable.getPageSize()>100 || pageable.getOffset()>Integer.MAX_VALUE)
            throw new FavoritePageException();
        Sort sort=pageable.getSort();
        for (var order:sort) if (!SORT_FIELDS.contains(order.getProperty())) throw new FavoritePageException();
        if (sort.isUnsorted()) sort=Sort.by(Sort.Direction.DESC,"createdAt");
        if (sort.getOrderFor("id")==null) sort=sort.and(Sort.by(Sort.Direction.DESC,"id"));
        var page=favorites.findActive(memberId,PageRequest.of(pageable.getPageNumber(),pageable.getPageSize(),sort));
        var people=page.getContent().stream().map(MemberFavorite::getMissingPerson)
                .filter(p -> !p.isDeleted() && p.isSourceAvailable() && "010".equals(p.getCategory())).toList();
        var children=assembler.fromAll(people);
        return FavoritePageResponse.from(page.map(f -> FavoriteResponse.from(f,children.get(f.getMissingPerson().getId()))));
    }
    private Member activeMember(UUID id,boolean lock) {
        var member=(lock ? members.findByIdForUpdate(id) : members.findById(id)).orElseThrow(AuthException::new);
        if (member.getStatus()!=MemberStatus.ACTIVE) throw new AuthException();
        return member;
    }
}
