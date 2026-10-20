package com.findus.backend.missing.service;

import com.findus.backend.missing.dto.MissingImageResponse;
import com.findus.backend.missing.dto.MissingPersonResponse;
import com.findus.backend.missing.entity.MissingPerson;
import com.findus.backend.missing.repository.MissingPersonImageRepository;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MissingResponseAssembler {
    private final MissingPersonImageRepository images;
    public MissingResponseAssembler(MissingPersonImageRepository images) { this.images=images; }
    public MissingPersonResponse from(MissingPerson person) { return fromAll(List.of(person)).get(person.getId()); }
    public Map<UUID,MissingPersonResponse> fromAll(List<MissingPerson> people) {
        var grouped=new HashMap<UUID,List<MissingImageResponse>>();
        var mainUrls=new HashMap<UUID,String>();
        for (var image:images.findMetadata(people.stream().map(MissingPerson::getId).toList())) {
            var response=MissingImageResponse.from(image);
            grouped.computeIfAbsent(image.missingPersonId(),key -> new ArrayList<>()).add(response);
            if (image.main()) mainUrls.put(image.missingPersonId(),response.url());
        }
        var result=new HashMap<UUID,MissingPersonResponse>();
        people.forEach(p -> {
            var list=grouped.getOrDefault(p.getId(),List.of());
            String fallback=list.isEmpty()?p.getPhotoUrl():list.get(0).url();
            result.put(p.getId(),MissingPersonResponse.from(p,list,mainUrls.getOrDefault(p.getId(),fallback)));
        });
        return result;
    }
}
