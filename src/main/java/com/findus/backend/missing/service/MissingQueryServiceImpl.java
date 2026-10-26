package com.findus.backend.missing.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.findus.backend.missing.dto.MissingPageResponse;
import com.findus.backend.missing.dto.MissingPersonResponse;
import com.findus.backend.missing.repository.MissingPersonRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class MissingQueryServiceImpl implements MissingQueryService {
    private final MissingPersonRepository repository;
    private final MissingResponseAssembler assembler;
    public MissingQueryServiceImpl(MissingPersonRepository repository,MissingResponseAssembler assembler) { this.repository=repository;this.assembler=assembler; }
    @Override public MissingPageResponse list(int page,int size) {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페이지 조건을 확인하세요.");
        // 발생일은 원본 yyyyMMdd 문자열이라 문자열 비교로 1년 이상 실종자만 고릅니다. 발생일이 없으면 제외됩니다.
        String cutoff=LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        var result=repository.findAvailable(cutoff,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurrenceDate","id")));
        var responses=assembler.fromAll(result.getContent());
        return new MissingPageResponse(result.map(p -> responses.get(p.getId())).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @Override public MissingPersonResponse detail(UUID id) {
        return assembler.from(repository.findAvailable(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"실종 정보를 찾을 수 없습니다.")));
    }
}
