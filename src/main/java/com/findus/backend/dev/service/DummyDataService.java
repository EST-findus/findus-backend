package com.findus.backend.dev.service;

import com.findus.backend.dev.dto.DummyDataResponse;

public interface DummyDataService {
    DummyDataResponse refreshStartup();
    DummyDataResponse createPersistent();
}
