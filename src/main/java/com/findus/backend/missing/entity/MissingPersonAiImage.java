package com.findus.backend.missing.entity;

import com.findus.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 입력 원본을 복제하지 않고 생성 결과와 목표 나이만 보관합니다. */
@Getter
@Entity
@Table(name="missing_person_ai_images",schema="findus")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MissingPersonAiImage extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="missing_person_id",nullable=false,updatable=false) private MissingPerson missingPerson;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="source_image_id",nullable=false,updatable=false) private MissingPersonImage sourceImage;
    @Column(name="target_age",nullable=false,updatable=false) private int targetAge;
    @Column(nullable=false,columnDefinition="bytea",updatable=false) private byte[] content;
    @Column(name="content_type",nullable=false,length=30,updatable=false) private String contentType;
    @Column(name="byte_size",nullable=false,updatable=false) private int byteSize;
    @Column(name="is_main",nullable=false) private boolean main;

    public MissingPersonAiImage(MissingPerson person,MissingPersonImage source,int age,byte[] generated) {
        if (person==null || source==null || person.getId()==null
                || !person.getId().equals(source.getMissingPerson().getId()) || source.isDeleted())
            throw new IllegalArgumentException("같은 아동의 활성 원본 사진만 참조할 수 있습니다.");
        if (age<0 || age>200) throw new IllegalArgumentException("목표 나이는 0~200 범위여야 합니다.");
        if (generated==null || generated.length==0 || generated.length>10485760)
            throw new IllegalArgumentException("AI 결과 사진은 0바이트 초과 10MB 이하여야 합니다.");
        // 호출자가 원본 배열을 수정해도 저장할 생성 결과가 바뀌지 않도록 복사합니다.
        content=generated.clone();
        contentType=validateType(content);
        byteSize=content.length;
        missingPerson=person;sourceImage=source;targetAge=age;
    }

    private static String validateType(byte[] bytes) {
        boolean jpeg=bytes.length>3 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8;
        boolean png=bytes.length>8 && bytes[0]==(byte)0x89 && bytes[1]==0x50 && bytes[2]==0x4e && bytes[3]==0x47;
        if (!jpeg && !png) throw new IllegalArgumentException("JPEG 또는 PNG 결과 사진만 저장할 수 있습니다.");
        try (var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("읽을 수 없는 사진");
            var reader=readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0)<1 || reader.getHeight(0)<1) throw new IOException("사진 크기 오류");
            } finally { reader.dispose(); }
        } catch (IOException e) { throw new IllegalArgumentException("결과 사진의 형식을 확인하세요.",e); }
        return jpeg?"image/jpeg":"image/png";
    }
    public void selectAsMain() {
        if (isDeleted()) throw new IllegalStateException("삭제된 사진은 대표로 지정할 수 없습니다.");
        main=true;
    }
    public void clearMain() { main=false; }
    @Override public void softDelete() { clearMain(); super.softDelete(); }
}
