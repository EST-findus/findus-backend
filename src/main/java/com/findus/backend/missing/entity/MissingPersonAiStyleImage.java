package com.findus.backend.missing.entity;

import com.findus.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대표 AI 사진에 머리 스타일을 입혀 미리 만들어 둔 결과입니다. */
@Getter
@Entity
@Table(name="missing_person_ai_style_images",schema="findus")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MissingPersonAiStyleImage extends BaseEntity {
    private static final Pattern STYLE_KEY=Pattern.compile("[a-z0-9_]{1,32}");
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="ai_image_id",nullable=false,updatable=false) private MissingPersonAiImage aiImage;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="missing_person_id",nullable=false,updatable=false) private MissingPerson missingPerson;
    @Column(nullable=false,length=1,updatable=false) private String sex;
    @Column(name="style_key",nullable=false,length=32,updatable=false) private String styleKey;
    @Column(nullable=false,columnDefinition="bytea",updatable=false) private byte[] content;
    @Column(name="content_type",nullable=false,length=30,updatable=false) private String contentType;
    @Column(name="byte_size",nullable=false,updatable=false) private int byteSize;
    @Column(columnDefinition="text",updatable=false) private String options;

    public MissingPersonAiStyleImage(MissingPersonAiImage aiImage,String sex,String styleKey,byte[] generated,String options) {
        if (aiImage==null || aiImage.isDeleted()) throw new IllegalArgumentException("활성 AI 사진에만 스타일을 저장할 수 있습니다.");
        if (!"M".equals(sex) && !"F".equals(sex)) throw new IllegalArgumentException("성별은 M 또는 F입니다.");
        if (styleKey==null || !STYLE_KEY.matcher(styleKey).matches()) throw new IllegalArgumentException("스타일 key 형식을 확인하세요.");
        if (generated==null || generated.length==0 || generated.length>10485760)
            throw new IllegalArgumentException("스타일 사진은 0바이트 초과 10MB 이하여야 합니다.");
        content=generated.clone();
        contentType=MissingPersonAiImage.validateType(content);
        byteSize=content.length;
        this.aiImage=aiImage;missingPerson=aiImage.getMissingPerson();
        this.sex=sex;this.styleKey=styleKey;this.options=options;
    }
}
