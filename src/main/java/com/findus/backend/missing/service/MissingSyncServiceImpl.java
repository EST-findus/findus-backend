package com.findus.backend.missing.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import com.findus.backend.missing.client.MissingPersonSource;
import com.findus.backend.missing.client.SafeDreamPhotoSource;
import com.findus.backend.missing.config.MissingProperties;
import com.findus.backend.missing.entity.MissingPerson;
import com.findus.backend.missing.entity.MissingPersonImage;
import com.findus.backend.missing.dto.SyncCounts;
import com.findus.backend.missing.dto.SyncRequests;
import com.findus.backend.missing.exception.MissingSyncException;
import com.findus.backend.missing.repository.MissingPersonRepository;
import com.findus.backend.missing.repository.MissingPersonImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissingSyncServiceImpl implements MissingSyncService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(MissingSyncServiceImpl.class);
    private final MissingPersonSource source;
    private final MissingPersonRepository repository;
    private final MissingPersonImageRepository images;
    private final MissingProperties properties;
    private final long interval;
    private static final Pattern INDEX=Pattern.compile("[?&]tknphotoFileIdx=([0-9]+)");
    public MissingSyncServiceImpl(MissingPersonSource source,MissingPersonRepository repository,
            MissingPersonImageRepository images,MissingProperties properties,
            @Value("${app.missing.photo-interval-ms:250}") long interval) {
        this.source=source;this.repository=repository;this.images=images;this.properties=properties;this.interval=Math.max(0,interval);
    }
    @Override
    @Transactional
    public SyncCounts sync() {
        if (!repository.tryLock()) throw new MissingSyncException("다른 실종자 동기화가 진행 중입니다.");
        int apiBefore=source.requestCount();
        var incoming=source.fetchAll();
        if (incoming.isEmpty()) throw new MissingSyncException("빈 전체 응답은 자동 반영하지 않습니다.");
        if (incoming.stream().anyMatch(row -> !"010".equals(row.category())))
            throw new MissingSyncException("실종아동 이외의 분류가 포함되어 기존 데이터를 유지합니다.");
        var existing=repository.findAllForSync();
        var byId=new HashMap<String,MissingPerson>();
        existing.forEach(p -> byId.put(p.getSourceId(),p));
        var seen=new HashSet<String>();
        var changed=new ArrayList<MissingPerson>();
        var now=LocalDateTime.now(ZoneOffset.UTC);
        int inserted=0,updated=0,unavailable=0;
        for (var row:incoming) {
            if (!seen.add(row.sourceId())) throw new MissingSyncException("중복된 식별자입니다.");
            var person=byId.get(row.sourceId());
            if (person==null) { person=new MissingPerson(row,now);byId.put(row.sourceId(),person);inserted++; }
            else if (person.apply(row,now)) updated++;
            changed.add(person);
        }
        for (var person:existing) {
            if (!seen.contains(person.getSourceId()) && person.isSourceAvailable()) {
                person.markUnavailable();changed.add(person);unavailable++;
            }
        }
        // DB 반영은 배치 전체 트랜잭션으로 묶여 DB 오류 시 회원 연결·사진도 함께 롤백됩니다.
        repository.saveAll(changed);
        var photoSource=new SafeDreamPhotoSource(properties.baseUrl(),interval);
        int imagesSaved=0,photoFailures=0,processed=0;
        for (var row:incoming) {
            var person=byId.get(row.sourceId());
            if (person.isDeleted()) continue;
            var photos=photoSource.fetch(row);
            boolean complete=photos.status().equals("DETAIL_LIST") || photos.status().equals("NO_PHOTOS");
            if (!complete) {
                photoFailures++;
                log.warn("실종아동 사진 일부 확인 불가: 식별자={}, 상태={}, 수신={}장",row.sourceId(),photos.status(),photos.images().size());
            }
            var previous=images.findForSync(person.getId());
            // 상세 조회 실패 시 기존 다중 사진을 대표 사진 하나로 덮어쓰지 않습니다.
            if (photos.status().equals("API_FALLBACK") && previous.stream().anyMatch(p -> !p.isDeleted())) continue;
            var byOrder=new HashMap<Integer,MissingPersonImage>();
            previous.forEach(p -> byOrder.put(p.getSortOrder(),p));
            var orders=new HashSet<Integer>();
            var saved=new ArrayList<MissingPersonImage>();
            for (var image:photos.images()) {
                var match=INDEX.matcher(image.sourceUrl());
                int order=match.find()?Integer.parseInt(match.group(1)):0;
                if (!orders.add(order)) throw new MissingSyncException("사진 순번이 중복되었습니다.");
                var entity=byOrder.get(order);
                if (entity==null) entity=new MissingPersonImage(person,order,image);
                else entity.apply(image);
                saved.add(entity);imagesSaved++;
            }
            if (complete) for (var image:previous) if (!orders.contains(image.getSortOrder()) && !image.isDeleted()) {
                image.softDelete();saved.add(image);
            }
            if (!saved.isEmpty()) images.saveAll(saved);
            if (++processed%20==0) log.info("실종아동 사진 동기화: {}/{}",processed,incoming.size());
        }
        return new SyncCounts(incoming.size(),inserted,updated,unavailable,imagesSaved,photoFailures,
                new SyncRequests(source.requestCount()-apiBefore,photoSource.detailRequests(),photoSource.imageRequests()));
    }
}
