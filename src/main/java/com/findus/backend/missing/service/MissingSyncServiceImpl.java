package com.findus.backend.missing.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import com.findus.backend.missing.client.MissingPersonSource;
import com.findus.backend.missing.entity.MissingPerson;
import com.findus.backend.missing.dto.SyncCounts;
import com.findus.backend.missing.exception.MissingSyncException;
import com.findus.backend.missing.repository.MissingPersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissingSyncServiceImpl implements MissingSyncService {
    private final MissingPersonSource source;
    private final MissingPersonRepository repository;
    public MissingSyncServiceImpl(MissingPersonSource source,MissingPersonRepository repository) { this.source=source;this.repository=repository; }
    @Override
    @Transactional
    public SyncCounts sync() {
        if (!repository.tryLock()) throw new MissingSyncException("다른 실종자 동기화가 진행 중입니다.");
        // 모든 페이지를 받은 뒤에만 반영하며, 오류가 나면 전체 트랜잭션을 롤백합니다.
        var incoming=source.fetchAll();
        if (incoming.isEmpty()) throw new MissingSyncException("빈 전체 응답은 자동 반영하지 않습니다.");
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
            if (person==null) { person=new MissingPerson(row,now); inserted++; }
            else if (person.apply(row,now)) updated++;
            changed.add(person);
        }
        for (var person:existing) {
            if (!seen.contains(person.getSourceId()) && person.isSourceAvailable()) {
                // 공개 중단과 발견 완료는 다릅니다. 원본 행은 삭제하지 않습니다.
                person.markUnavailable(); changed.add(person); unavailable++;
            }
        }
        repository.saveAll(changed);
        return new SyncCounts(incoming.size(),inserted,updated,unavailable);
    }
}
