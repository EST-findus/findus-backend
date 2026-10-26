package com.findus.backend.missing.service;

import com.findus.backend.aistyle.config.AiStyleCatalog;
import com.findus.backend.missing.dto.MissingAiImageResponse;
import com.findus.backend.missing.dto.MissingImageContent;
import com.findus.backend.missing.repository.MissingPersonAiImageRepository;
import com.findus.backend.missing.repository.MissingPersonAiStyleImageRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
        return aiImages.findPublicMain(personId).map(main -> {
            String photo="/api/missing-persons/"+personId+"/ai-image/photo";
            var saved=new HashSet<>(styles.findActiveKeys(main.id()));
            // 성별이 바뀌거나 설정에서 뺀 스타일은 저장돼 있어도 보여주지 않습니다.
            List<MissingAiImageResponse.Style> list=AiStyleCatalog.sexOf(main.sex()).map(catalog::styles).orElse(List.of()).stream()
                    .filter(style -> saved.contains(style.key()))
                    .map(style -> new MissingAiImageResponse.Style(style.key(),style.name(),
                            UriComponentsBuilder.fromPath(photo).queryParam("style",style.key()).build().toUriString()))
                    .toList();
            return new MissingAiImageResponse(main.id(),main.targetAge(),main.createdAt(),photo,list,MissingAiImageResponse.NOTICE);
        });
    }

    @Override public Optional<MissingImageContent> photo(UUID personId,String style) {
        if (style==null || style.isBlank()) return aiImages.findPublicMainContent(personId);
        return styles.findPublicContent(personId,style.strip());
    }
}
