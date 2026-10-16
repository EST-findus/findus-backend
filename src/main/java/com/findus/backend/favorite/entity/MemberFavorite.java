package com.findus.backend.favorite.entity;

import java.util.UUID;
import com.findus.backend.common.entity.BaseEntity;
import com.findus.backend.member.entity.Member;
import com.findus.backend.missing.entity.MissingPerson;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="member_favorites",schema="findus",uniqueConstraints=
        @UniqueConstraint(name="uk_member_favorites_member_missing",columnNames={"member_id","missing_person_id"}))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MemberFavorite extends BaseEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    @Column(nullable=false,updatable=false)
    private UUID id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="member_id",nullable=false,updatable=false)
    private Member member;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="missing_person_id",nullable=false,updatable=false)
    private MissingPerson missingPerson;

    public MemberFavorite(Member member,MissingPerson missingPerson) {
        if (member==null || missingPerson==null) throw new IllegalArgumentException("회원과 실종아동 정보가 필요합니다.");
        this.member=member;this.missingPerson=missingPerson;
    }

    /** 취소한 관심을 동일한 UUID로 복원하며 최초 등록 시각은 보존합니다. */
    public void reactivate() { restoreDeletedAt(); }
}
