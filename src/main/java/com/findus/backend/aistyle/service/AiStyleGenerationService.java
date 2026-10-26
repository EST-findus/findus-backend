package com.findus.backend.aistyle.service;

import com.findus.backend.aiserver.client.AiServerClient;
import com.findus.backend.aiserver.exception.AiServerException;
import com.findus.backend.aiserver.exception.AiServerException.Reason;
import com.findus.backend.aistyle.config.AiStyleCatalog;
import com.findus.backend.aistyle.dto.AiStyleGenerationStatus;
import com.findus.backend.missing.dto.MissingAiImageMetadata;
import com.findus.backend.missing.repository.MissingPersonAiImageRepository;
import com.findus.backend.missing.repository.MissingPersonAiStyleImageRepository;
import com.findus.backend.missing.service.MissingImageManagementService;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 대표 AI 사진마다 아동 성별에 맞는 머리 스타일을 미리 만들어 저장합니다.
 * GPU가 한 장이라 한 번에 한 장씩 순서대로 요청하며, 그래야 AI 서버가 머리 모델을 내리지 않고 이어서 씁니다.
 */
@Service
public class AiStyleGenerationService {
    private static final Logger log = LoggerFactory.getLogger(AiStyleGenerationService.class);
    private static final int MAX_ERRORS = 20;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MissingPersonAiImageRepository aiImages;
    private final MissingPersonAiStyleImageRepository styles;
    private final MissingImageManagementService images;
    private final AiServerClient client;
    private final AiStyleCatalog catalog;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "ai-style-generation");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lock = new Object();
    // region 진행 상태 (lock으로 보호)
    private boolean running;
    private String state = "IDLE";
    private LocalDateTime startedAt, finishedAt;
    private int total, created, skipped, failed;
    private String current;
    private final Deque<String> errors = new ArrayDeque<>();
    // endregion

    record Job(UUID aiImageId, UUID personId, String sex, String styleKey) {}

    public AiStyleGenerationService(MissingPersonAiImageRepository aiImages, MissingPersonAiStyleImageRepository styles,
                                    MissingImageManagementService images, AiServerClient client, AiStyleCatalog catalog) {
        this.aiImages = aiImages;
        this.styles = styles;
        this.images = images;
        this.client = client;
        this.catalog = catalog;
    }

    /** 실행 중이면 비어 있습니다. personId가 없으면 공개 중인 모든 아동의 대표 AI 사진이 대상입니다. */
    public Optional<AiStyleGenerationStatus> start(UUID personId) {
        synchronized (lock) {
            if (running) return Optional.empty();
            running = true;
        }
        List<Job> jobs;
        try {
            jobs = plan(personId);
        } catch (RuntimeException e) {
            synchronized (lock) { running = false; }
            throw e;
        }
        synchronized (lock) {
            state = "RUNNING";
            startedAt = now();
            finishedAt = null;
            total = jobs.size();
            created = skipped = failed = 0;
            current = null;
            errors.clear();
        }
        executor.submit(() -> run(jobs));
        return Optional.of(status());
    }

    public AiStyleGenerationStatus status() {
        synchronized (lock) {
            return new AiStyleGenerationStatus(state, startedAt, finishedAt, total, created, skipped, failed, current, List.copyOf(errors));
        }
    }

    /** 이미 저장된 스타일은 빼고 만들 목록만 고릅니다. 다시 실행하면 남은 것만 이어서 만듭니다. */
    private List<Job> plan(UUID personId) {
        List<MissingAiImageMetadata> targets = personId == null ? aiImages.findPublicMains()
                : aiImages.findPublicMain(personId).stream().toList();
        var jobs = new ArrayList<Job>();
        for (var target : targets) {
            var sex = AiStyleCatalog.sexOf(target.sex());
            if (sex.isEmpty()) continue;
            var saved = new HashSet<>(styles.findActiveKeys(target.id()));
            for (var style : catalog.styles(sex.get()))
                if (!saved.contains(style.key())) jobs.add(new Job(target.id(), target.missingPersonId(), sex.get(), style.key()));
        }
        return jobs;
    }

    private void run(List<Job> jobs) {
        // 얼굴을 찾지 못한 사진은 다른 스타일도 실패하므로 남은 스타일을 건너뜁니다.
        var unusable = new HashSet<UUID>();
        String finalState = "DONE";
        try {
            for (var job : jobs) {
                synchronized (lock) { current = job.personId() + " / " + job.styleKey(); }
                if (unusable.contains(job.aiImageId())) { fail(job, "같은 사진에서 얼굴을 찾지 못해 건너뜀"); continue; }
                var source = aiImages.findActive(job.aiImageId());
                if (source.isEmpty()) { skip(); continue; }
                try {
                    var result = hairWithRetry(source.get().getContent(), job);
                    String options = JSON.writeValueAsString(Map.of("resultId", result.resultId(), "seconds", result.seconds(), "info", result.info()));
                    images.saveAiStyle(job.aiImageId(), job.sex(), job.styleKey(), result.png(), options);
                    synchronized (lock) { created++; }
                } catch (AiServerException e) {
                    if (e.reason() == Reason.UNAVAILABLE) {
                        log.warn("AI 서버 연결 실패로 머리 스타일 생성을 중단합니다: {}", job, e);
                        fail(job, e.getMessage());
                        finalState = "FAILED";
                        break;
                    }
                    if (e.reason() == Reason.BAD_INPUT) unusable.add(job.aiImageId());
                    fail(job, e.getMessage());
                } catch (DataIntegrityViolationException e) {
                    // 수동 저장 등으로 그 사이 같은 스타일이 생겼습니다.
                    skip();
                } catch (RuntimeException e) {
                    log.warn("머리 스타일 생성 실패: {}", job, e);
                    fail(job, "저장하지 못했습니다.");
                }
            }
        } finally {
            synchronized (lock) {
                state = finalState;
                finishedAt = now();
                current = null;
                running = false;
            }
        }
    }

    private AiServerClient.HairResult hairWithRetry(byte[] image, Job job) {
        int retries = catalog.properties().busyRetries();
        for (int attempt = 0; ; attempt++) {
            try {
                return client.hair(image, job.sex(), job.styleKey());
            } catch (AiServerException e) {
                if (e.reason() != Reason.BUSY || attempt >= retries) throw e;
                try {
                    Thread.sleep(catalog.properties().busyRetryDelay().toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private void skip() {
        synchronized (lock) { skipped++; }
    }

    private void fail(Job job, String reason) {
        synchronized (lock) {
            failed++;
            errors.addFirst(job.personId() + " / " + job.styleKey() + ": " + reason);
            while (errors.size() > MAX_ERRORS) errors.removeLast();
        }
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
