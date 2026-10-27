package com.findus.backend.missing.service;

import com.findus.backend.aistyle.config.AiStyleCatalog;
import com.findus.backend.missing.dto.MissingAiImageMetadata;
import com.findus.backend.missing.dto.MissingAiImageResponse;
import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.repository.MissingPersonAiImageRepository;
import com.findus.backend.missing.repository.MissingPersonAiStyleImageRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional(readOnly=true)
public class MissingAiImageQueryServiceImpl implements MissingAiImageQueryService {
    private final MissingPersonAiImageRepository aiImages;
    private final MissingPersonAiStyleImageRepository styles;
    private final AiStyleCatalog catalog;
    public MissingAiImageQueryServiceImpl(MissingPersonAiImageRepository aiImages,MissingPersonAiStyleImageRepository styles,AiStyleCatalog catalog) {
        this.aiImages=aiImages;this.styles=styles;this.catalog=catalog;
    }

    @Override public Optional<MissingAiImageResponse> mainAiImage(UUID personId) {
        // 정보 응답에는 사진 바이트를 넣지 않습니다. 사진은 url로 따로 받아 브라우저가 캐시합니다.
        // 대표(is_main)가 첫 항목이고, 대표가 없으면 가장 먼저 만든 사진을 대표 자리에 씁니다.
        List<MissingAiImageMetadata> all=aiImages.findPublicAll(personId);
        return all.stream().findFirst().map(main -> {
            String photo="/api/missing-persons/"+personId+"/ai-image/photo";
            // 스타일 사진 조회는 is_main인 AI 사진 기준이라, 대표가 지정되지 않았으면 스타일을 보여주지 않습니다.
            Set<String> saved=main.main() ? new HashSet<>(styles.findActiveKeys(main.id())) : Set.of();
            // 성별이 바뀌거나 설정에서 뺀 스타일은 저장돼 있어도 보여주지 않습니다.
            List<MissingAiImageResponse.Style> list=AiStyleCatalog.sexOf(main.sex()).map(catalog::styles).orElse(List.of()).stream()
                    .filter(style -> saved.contains(style.key()))
                    .map(style -> new MissingAiImageResponse.Style(style.key(),style.name(),
                            UriComponentsBuilder.fromPath(photo).queryParam("style",style.key()).build().toUriString()))
                    .toList();
            // 대표는 기존 주소를 그대로 써서 목록 카드와 같은 브라우저 캐시를 씁니다.
            List<MissingAiImageResponse.Image> images=all.stream()
                    .map(image -> {
                        boolean first=image.id().equals(main.id());
                        return new MissingAiImageResponse.Image(image.id(),first,image.targetAge(),image.createdAt(),
                                first ? photo : "/api/missing-persons/"+personId+"/ai-image/photos/"+image.id());
                    })
                    .toList();
            return new MissingAiImageResponse(main.id(),main.targetAge(),main.createdAt(),photo,list,MissingAiImageResponse.NOTICE,images);
        });
    }

    @Override public Optional<MissingImageContent> photoById(UUID personId,UUID aiImageId) {
        return aiImages.findPublicContent(personId,aiImageId);
    }

    @Override public Optional<MissingImageContent> photo(UUID personId,String style) {
        if (style==null || style.isBlank()) return aiImages.findPublicFirstContent(personId);
        return styles.findPublicContent(personId,style.strip());
    }
}
