package com.findus.backend.missing.service;

import java.util.UUID;
import com.findus.backend.missing.dto.MissingPageResponse;
import com.findus.backend.missing.dto.MissingPersonResponse;
public interface MissingQueryService {
    MissingPageResponse list(int page,int size);
    MissingPersonResponse detail(UUID id);
}
