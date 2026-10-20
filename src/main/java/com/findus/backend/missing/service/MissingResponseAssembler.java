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
        for (var image:images.findMetadata(people.stream().map(MissingPerson::getId).toList()))
            grouped.computeIfAbsent(image.missingPersonId(),key -> new ArrayList<>()).add(MissingImageResponse.from(image));
        var result=new HashMap<UUID,MissingPersonResponse>();
        people.forEach(p -> result.put(p.getId(),MissingPersonResponse.from(p,grouped.getOrDefault(p.getId(),List.of()))));
        return result;
    }
}
