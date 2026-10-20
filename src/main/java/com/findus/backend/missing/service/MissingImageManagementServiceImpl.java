package com.findus.backend.missing.service;

import com.findus.backend.missing.entity.MissingPerson;
import com.findus.backend.missing.entity.MissingPersonImage;
import com.findus.backend.missing.entity.MissingPersonAiImage;
import com.findus.backend.missing.repository.MissingPersonRepository;
import com.findus.backend.missing.repository.MissingPersonImageRepository;
import com.findus.backend.missing.repository.MissingPersonAiImageRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MissingImageManagementServiceImpl implements MissingImageManagementService {
    private final MissingPersonRepository persons;
    private final MissingPersonImageRepository originals;
    private final MissingPersonAiImageRepository aiImages;
    public MissingImageManagementServiceImpl(MissingPersonRepository persons,MissingPersonImageRepository originals,
            MissingPersonAiImageRepository aiImages) {
        this.persons=persons;this.originals=originals;this.aiImages=aiImages;
    }
    private MissingPerson lockAvailablePerson(UUID id) {
        var person=persons.findForImageUpdate(id).orElseThrow(() -> new IllegalArgumentException("실종아동을 찾을 수 없습니다."));
        if (person.isDeleted() || !person.isSourceAvailable() || !"010".equals(person.getCategory()))
            throw new IllegalArgumentException("공개 중인 실종아동만 사진을 관리할 수 있습니다.");
        return person;
    }
    @Override public void ensureOriginalMain(UUID personId) {
        lockAvailablePerson(personId);
        var active=originals.findForSync(personId).stream().filter(i -> !i.isDeleted()).toList();
        if (active.stream().anyMatch(MissingPersonImage::isMain)) return;
        active.stream().min(Comparator.comparingInt(MissingPersonImage::getSortOrder).thenComparing(MissingPersonImage::getId))
                .ifPresent(image -> { image.selectAsMain();originals.saveAll(List.of(image)); });
    }
    @Override public void selectOriginalMain(UUID personId,UUID imageId) {
        lockAvailablePerson(personId);
        var images=originals.findForSync(personId);
        var chosen=images.stream().filter(i -> i.getId().equals(imageId) && !i.isDeleted()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("같은 아동의 활성 원본 사진을 선택하세요."));
        if (chosen.isMain()) return;
        var previous=images.stream().filter(MissingPersonImage::isMain).toList();
        previous.forEach(MissingPersonImage::clearMain);
        // 이전 대표 해제를 먼저 flush해야 부분 UNIQUE 인덱스와 충돌하지 않습니다.
        if (!previous.isEmpty()) originals.saveAll(previous);
        chosen.selectAsMain();originals.saveAll(List.of(chosen));
    }
    @Override public UUID saveAiResult(UUID personId,UUID sourceImageId,int targetAge,byte[] generated) {
        var person=lockAvailablePerson(personId);
        var source=originals.findPublic(personId,sourceImageId)
                .orElseThrow(() -> new IllegalArgumentException("같은 아동의 활성 원본 사진을 선택하세요."));
        var image=aiImages.save(new MissingPersonAiImage(person,source,targetAge,generated));
        ensureAiMain(personId);
        return image.getId();
    }
    private void ensureAiMain(UUID personId) {
        var active=aiImages.findForManagement(personId).stream().filter(i -> !i.isDeleted()).toList();
        if (active.stream().anyMatch(MissingPersonAiImage::isMain)) return;
        active.stream().min(Comparator.comparing(MissingPersonAiImage::getCreatedAt).thenComparing(MissingPersonAiImage::getId))
                .ifPresent(image -> { image.selectAsMain();aiImages.saveAll(List.of(image)); });
    }
    @Override public void selectAiMain(UUID personId,UUID imageId) {
        lockAvailablePerson(personId);
        var images=aiImages.findForManagement(personId);
        var chosen=images.stream().filter(i -> i.getId().equals(imageId) && !i.isDeleted()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("같은 아동의 활성 AI 사진을 선택하세요."));
        if (chosen.isMain()) return;
        var previous=images.stream().filter(MissingPersonAiImage::isMain).toList();
        previous.forEach(MissingPersonAiImage::clearMain);
        if (!previous.isEmpty()) aiImages.saveAll(previous);
        chosen.selectAsMain();aiImages.saveAll(List.of(chosen));
    }
    @Override public void deleteAiResult(UUID personId,UUID imageId) {
        lockAvailablePerson(personId);
        var image=aiImages.findForManagement(personId).stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("같은 아동의 AI 사진을 선택하세요."));
        if (image.isDeleted()) return;
        image.softDelete();aiImages.saveAll(List.of(image));ensureAiMain(personId);
    }
}
