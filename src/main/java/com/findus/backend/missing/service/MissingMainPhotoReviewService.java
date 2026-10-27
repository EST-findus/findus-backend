package com.findus.backend.missing.service;

import com.findus.backend.missing.dto.MainPhotoReviewPage;
import java.util.UUID;

public interface MissingMainPhotoReviewService {
    MainPhotoReviewPage page(int page,int size,boolean multiOnly);
    void selectMain(UUID personId,UUID imageId);
}
