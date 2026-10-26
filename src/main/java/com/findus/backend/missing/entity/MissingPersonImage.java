package com.findus.backend.missing.entity;

import com.findus.backend.common.entity.BaseEntity;
import com.findus.backend.missing.client.SafeDreamPhotoSource;
import jakarta.persistence.*;
import java.util.Base64;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name="missing_person_images", schema="findus")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MissingPersonImage extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="missing_person_id", nullable=false) private MissingPerson missingPerson;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="source_url", nullable=false, length=1024) private String sourceUrl;
    @Column(name="content_type", nullable=false, length=30) private String contentType;
    @Column(nullable=false, columnDefinition="bytea") private byte[] content;
    @Column(name="byte_size", nullable=false) private int byteSize;
    @Column(nullable=false, length=64) private String sha256;
    @Column(name="is_main", nullable=false) private boolean main;

    public MissingPersonImage(MissingPerson person, int order, SafeDreamPhotoSource.Image image) {
        missingPerson=person; sortOrder=order; apply(image);
    }
    public void apply(SafeDreamPhotoSource.Image image) {
        String type=image.dataUrl().substring(5,image.dataUrl().indexOf(';'));
        if (!type.equals("image/jpeg") && !type.equals("image/png")) throw new IllegalArgumentException("사진 형식을 확인하세요.");
        if (!image.sha256().equals(sha256)) {
            content=Base64.getDecoder().decode(image.dataUrl().substring(image.dataUrl().indexOf(',')+1));
            if (content.length==0 || content.length>2097152) throw new IllegalArgumentException("사진 크기를 확인하세요.");
            byteSize=content.length; sha256=image.sha256(); contentType=type;
        }
        sourceUrl=image.sourceUrl(); restoreDeletedAt();
    }
    public void selectAsMain() {
        if (isDeleted()) throw new IllegalStateException("삭제된 사진은 대표로 지정할 수 없습니다.");
        main=true;
    }
    public void clearMain() { main=false; }
    @Override public void softDelete() { clearMain(); super.softDelete(); }
}
