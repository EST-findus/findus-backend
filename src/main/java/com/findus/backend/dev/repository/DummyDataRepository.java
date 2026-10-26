package com.findus.backend.dev.repository;

import java.util.UUID;

public interface DummyDataRepository {
    void lock();
    boolean isInitialized(String dataSet);
    void markInitialized(String dataSet);
    void saveMember(UUID id, String email, String passwordHash, String name, String nickname);
    void savePost(UUID id, UUID memberId, String title, String content);
    void saveComment(UUID id, UUID postId, UUID memberId, String content);
}
