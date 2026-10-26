package com.findus.backend.missing.service;

import com.findus.backend.missing.dto.MainPhotoReviewPage;
import com.findus.backend.missing.dto.MissingImageMetadata;
import com.findus.backend.missing.entity.MissingPerson;
import com.findus.backend.missing.repository.MissingPersonImageRepository;
import com.findus.backend.missing.repository.MissingPersonRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
@ConditionalOnProperty(name="app.main-photo-tool.enabled",havingValue="true")
public class MissingMainPhotoReviewServiceImpl implements MissingMainPhotoReviewService {
    private final MissingPersonRepository persons;
    private final MissingPersonImageRepository images;
    private final MissingImageManagementService management;
    private final String dbHost;
    public MissingMainPhotoReviewServiceImpl(MissingPersonRepository persons,MissingPersonImageRepository images,
            MissingImageManagementService management,@Value("${spring.datasource.url}") String url) {
        this.persons=persons;this.images=images;this.management=management;this.dbHost=host(url);
    }
    @Override @Transactional(readOnly=true)
    public MainPhotoReviewPage page(int page,int size,boolean multiOnly) {
        if (page<0 || size<1 || size>50) throw new IllegalArgumentException("page는 0 이상, size는 1~50으로 요청하세요.");
        // 공개 목록과 같은 순서로 고정해 검토 중 페이지가 흔들리지 않게 합니다.
        var result=persons.findForMainReview(multiOnly?2:1,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurrenceDate","id")));
        var byPerson=images.findMetadata(result.getContent().stream().map(MissingPerson::getId).toList()).stream()
                .collect(Collectors.groupingBy(MissingImageMetadata::missingPersonId));
        var content=result.getContent().stream().map(p -> new MainPhotoReviewPage.Person(p.getId(),p.getSourceId(),p.getName(),p.getSex(),
                p.getOccurrenceDate(),p.getAge(),p.getAgeNow(),
                byPerson.getOrDefault(p.getId(),List.of()).stream().map(MainPhotoReviewPage.Image::from).toList())).toList();
        return new MainPhotoReviewPage(dbHost,page,result.getTotalPages(),result.getTotalElements(),content);
    }
    @Override public void selectMain(UUID personId,UUID imageId) { management.selectOriginalMain(personId,imageId); }

    /** jdbc:postgresql://host:port/db?... 에서 host:port만 남겨 계정·옵션은 화면에 보내지 않습니다. */
    static String host(String url) {
        int start=url.indexOf("//");
        if (start<0) return "알 수 없음";
        String rest=url.substring(start+2);
        int end=rest.indexOf('/');
        return end<0?rest:rest.substring(0,end);
    }
}
