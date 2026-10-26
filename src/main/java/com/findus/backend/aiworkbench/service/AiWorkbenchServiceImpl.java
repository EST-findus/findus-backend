package com.findus.backend.aiworkbench.service;

import com.findus.backend.aiworkbench.dto.AiWorkbenchSave;
import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveCommand;
import com.findus.backend.aiworkbench.dto.AiWorkbenchSaveResponse;
import com.findus.backend.aiworkbench.dto.StoredAiImage;
import com.findus.backend.aiworkbench.exception.AiWorkbenchConflictException;
import com.findus.backend.aiworkbench.repository.AiWorkbenchSaveRepository;
import com.findus.backend.missing.service.MissingImageManagementService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("local")
@ConditionalOnProperty(name="app.aiworkbench.enabled",havingValue="true")
public class AiWorkbenchServiceImpl implements AiWorkbenchService {
    private static final Pattern RESULT_ID=Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern IMAGE_KEY=Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int MAX_OPTIONS=20000;
    private final MissingImageManagementService images;
    private final AiWorkbenchSaveRepository saves;
    private final TransactionTemplate tx;
    public AiWorkbenchServiceImpl(MissingImageManagementService images,AiWorkbenchSaveRepository saves,TransactionTemplate tx) {
        this.images=images;this.saves=saves;this.tx=tx;
    }

    @Override public AiWorkbenchSaveResponse save(AiWorkbenchSaveCommand c) {
        validate(c);
        String hash=sha256(c.image());
        var existing=saves.findByRequest(c.resultId(),c.imageKey());
        if (existing.isPresent()) return replay(existing.get(),c,hash);
        try {
            // AI 사진과 이력을 한 트랜잭션으로 묶어 이력 없는 사진이 남지 않게 합니다.
            var saved=tx.execute(status -> {
                UUID aiImageId=images.saveAiResult(c.personId(),c.sourceImageId(),c.targetAge(),c.image());
                var row=new AiWorkbenchSave(UUID.randomUUID(),aiImageId,c.personId(),c.sourceImageId(),c.targetAge(),
                        c.resultId(),c.imageKey(),hash,c.options(),LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
                saves.insert(row);
                return row;
            });
            return AiWorkbenchSaveResponse.from(saved,true);
        } catch (DuplicateKeyException e) {
            // 동시에 들어온 같은 요청은 위 트랜잭션이 통째로 롤백되므로, 먼저 끝난 저장 결과를 돌려줍니다.
            var winner=saves.findByRequest(c.resultId(),c.imageKey()).orElseThrow(() -> e);
            return replay(winner,c,hash);
        }
    }
    @Override public List<AiWorkbenchSaveResponse> history(UUID personId,String resultId) {
        if (personId==null && (resultId==null || resultId.isBlank()))
            throw new IllegalArgumentException("personId 또는 resultId 중 하나가 필요합니다.");
        var rows=personId!=null?saves.findByPerson(personId):saves.findByResult(resultId);
        return rows.stream().filter(r -> resultId==null || resultId.isBlank() || r.resultId().equals(resultId))
                .map(r -> AiWorkbenchSaveResponse.from(r,false)).toList();
    }
    @Override public Optional<StoredAiImage> savedImage(UUID aiImageId) { return saves.findSavedImage(aiImageId); }

    private AiWorkbenchSaveResponse replay(AiWorkbenchSave saved,AiWorkbenchSaveCommand c,String hash) {
        boolean same=saved.contentSha256().equals(hash) && saved.personId().equals(c.personId())
                && saved.sourceImageId().equals(c.sourceImageId()) && saved.targetAge()==c.targetAge();
        if (!same) throw new AiWorkbenchConflictException("같은 결과 이미지가 이미 다른 사진 또는 다른 연결 정보로 저장되어 있습니다.");
        return AiWorkbenchSaveResponse.from(saved,false);
    }
    private static void validate(AiWorkbenchSaveCommand c) {
        if (c.personId()==null || c.sourceImageId()==null) throw new IllegalArgumentException("아동과 원본 사진을 선택하세요.");
        // AI 서버가 생성할 수 있는 범위만 받습니다. 엔티티의 0~200보다 좁습니다.
        if (c.targetAge()<1 || c.targetAge()>100) throw new IllegalArgumentException("목표 나이는 1~100 범위여야 합니다.");
        if (c.resultId()==null || !RESULT_ID.matcher(c.resultId()).matches()) throw new IllegalArgumentException("resultId 형식을 확인하세요.");
        if (c.imageKey()==null || !IMAGE_KEY.matcher(c.imageKey()).matches()) throw new IllegalArgumentException("imageKey 형식을 확인하세요.");
        if (c.options()!=null && c.options().length()>MAX_OPTIONS) throw new IllegalArgumentException("생성 옵션이 너무 깁니다.");
        if (c.image()==null || c.image().length==0) throw new IllegalArgumentException("저장할 결과 사진이 없습니다.");
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
