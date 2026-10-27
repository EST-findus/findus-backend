package com.findus.backend.missing.service;

import com.findus.backend.missing.dto.MissingImageResponse;
import com.findus.backend.missing.dto.MissingPersonResponse;
import com.findus.backend.missing.entity.MissingPerson;
import com.findus.backend.missing.repository.MissingPersonAiImageRepository;
import com.findus.backend.missing.repository.MissingPersonImageRepository;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MissingResponseAssembler {
    private final MissingPersonImageRepository images;
    private final MissingPersonAiImageRepository aiImages;
    public MissingResponseAssembler(MissingPersonImageRepository images,MissingPersonAiImageRepository aiImages) {
        this.images=images;this.aiImages=aiImages;
    }
    public MissingPersonResponse from(MissingPerson person) { return fromAll(List.of(person)).get(person.getId()); }
    public Map<UUID,MissingPersonResponse> fromAll(List<MissingPerson> people) {
        var ids=people.stream().map(MissingPerson::getId).toList();
        var grouped=new HashMap<UUID,List<MissingImageResponse>>();
        var mainUrls=new HashMap<UUID,String>();
        for (var image:images.findMetadata(ids)) {
            var response=MissingImageResponse.from(image);
            grouped.computeIfAbsent(image.missingPersonId(),key -> new ArrayList<>()).add(response);
            if (image.main()) mainUrls.put(image.missingPersonId(),response.url());
        }
        // 목록에서 AI 사진이 있는 아동을 구분할 수 있도록 AI 사진 여부만 한 번에 조회합니다. 대표가 없어도 첫 AI 사진을 보여줍니다.
        var withAi=aiImages.findPersonIdsWithPublicAi(ids);
        var result=new HashMap<UUID,MissingPersonResponse>();
        people.forEach(p -> {
            var list=grouped.getOrDefault(p.getId(),List.of());
            String fallback=list.isEmpty()?p.getPhotoUrl():list.get(0).url();
            result.put(p.getId(),MissingPersonResponse.from(p,list,mainUrls.getOrDefault(p.getId(),fallback),withAi.contains(p.getId())));
        });
        return result;
    }
}
