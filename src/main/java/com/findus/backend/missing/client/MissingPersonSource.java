package com.findus.backend.missing.client;

import java.util.List;
import com.findus.backend.missing.dto.SourcePerson;

/** 실제 HTTP 호출과 테스트용 데이터를 분리하는 경계입니다. */
public interface MissingPersonSource {
    List<SourcePerson> fetchAll();
    default int requestCount() { return 0; }
}
