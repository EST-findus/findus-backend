package com.findus.backend.dev.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.findus.backend.dev.dto.DummyDataResponse;
import com.findus.backend.dev.repository.DummyDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
@ConditionalOnProperty(name = "app.dummy.enabled", havingValue = "true")
@Transactional
public class DummyDataServiceImpl implements DummyDataService {
    private final DummyDataRepository repository;
    private final PasswordEncoder encoder;
    private final String password;

    public DummyDataServiceImpl(DummyDataRepository repository, PasswordEncoder encoder,
            @Value("${app.dummy.password}") String password) {
        this.repository = repository;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public DummyDataResponse refreshStartup() {
        repository.lock();
        return generate("startup", 1, 3);
    }

    @Override
    public DummyDataResponse createPersistent() {
        repository.lock();
        // 이미 생성한 데이터를 수정·삭제했더라도 다시 만들거나 덮어쓰지 않습니다.
        if (repository.isInitialized("persistent")) {
            return new DummyDataResponse("persistent", false, 5, 36, 72);
        }
        var result = generate("persistent", 5, 36);
        repository.markInitialized("persistent");
        return result;
    }

    private DummyDataResponse generate(String dataSet, int memberCount, int postCount) {
        // 같은 테스트 비밀번호는 한 번만 해싱하여 초기화 비용을 줄입니다.
        String hash = encoder.encode(password);
        String prefix = dataSet.equals("startup") ? "시작갱신" : "고정";
        for (int i = 1; i <= memberCount; i++) {
            repository.saveMember(id(dataSet, "member", i),
                    "dummy." + dataSet + i + "@example.test", hash,
                    prefix + "회원" + i, prefix + "닉네임" + i);
        }
        for (int i = 1; i <= postCount; i++) {
            UUID postId = id(dataSet, "post", i);
            repository.savePost(postId, id(dataSet, "member", (i - 1) % memberCount + 1),
                    "[" + prefix + " 더미] 게시글 " + i,
                    "게시글·댓글 API 확인용 가상 데이터입니다.\n게시글 번호: " + i);
            for (int j = 1; j <= 2; j++) {
                repository.saveComment(id(dataSet, "comment", (i - 1) * 2 + j), postId,
                        id(dataSet, "member", (i + j - 1) % memberCount + 1),
                        prefix + " 테스트 댓글 " + j + " (게시글 " + i + ")");
            }
        }
        return new DummyDataResponse(dataSet, true, memberCount, postCount, postCount * 2);
    }

    // 세트별 UUID를 일정하게 생성하여 재시작 때 중복 행을 만들지 않습니다.
    private static UUID id(String dataSet, String type, int number) {
        return UUID.nameUUIDFromBytes(("findus:dummy:v1:" + dataSet + ":" + type + ":" + number)
                .getBytes(StandardCharsets.UTF_8));
    }
}
