package com.findus.backend.missing.service;

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
    public MissingQueryServiceImpl(MissingPersonRepository repository) { this.repository=repository; }
    @Override public MissingPageResponse list(int page,int size) {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페이지 조건을 확인하세요.");
        var result=repository.findAvailable(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"occurrenceDate","id")));
        return new MissingPageResponse(result.map(MissingPersonResponse::from).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @Override public MissingPersonResponse detail(UUID id) {
        return MissingPersonResponse.from(repository.findAvailable(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"실종 정보를 찾을 수 없습니다.")));
    }
}
