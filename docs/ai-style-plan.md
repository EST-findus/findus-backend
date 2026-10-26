# 계획: AI 이미지 조회 + 미리 만들어 둔 머리 스타일 보여주기

> 구현 결과와 사용법은 [ai-style-images.md](ai-style-images.md)를 보세요. 5번(workbench 수동 저장)은 아직 구현하지 않았습니다.

## Context
- 실종아동 상세 화면에서 DB에 저장된 AI(나이 변환) 사진을 보여주고, 사용자가 스타일 버튼을 누르면 **미리 만들어 저장해 둔** 머리 스타일 사진으로 즉시 바꿔 보여주려 한다.
- 머리 변환은 1장 40~80초(GPU 1장, 한 번에 1요청)라 실시간 생성은 불가 → 사전 생성 + DB 저장 + 조회만.
- 결정 사항(사용자 답변):
  - 나이 변환(AI 이미지) 자체는 **자동 생성하지 않음**. 지금처럼 workbench에서 수동으로 만든 것만 사용.
  - 스타일은 **이미 저장된 AI 이미지 위에** 적용. AI 이미지가 있는 아동만 대상.
  - **아동 성별에 맞는 기본 스타일만**: 남(buzz, short, short_bang) 3장 / 여(short, bob, bob_bang, long, long_bang) 5장. 색은 참조 사진 색 그대로.
  - 생성 경로는 **둘 다**: 관리자가 수동으로 실행하는 일괄 생성(Java → AI 서버) + workbench 수동 생성·저장(후순위).
- 커밋 안 된 `aging` 패키지는 엔드포인트로는 쓰지 않고, AI 서버 호출 부분만 재사용한다.

## 1. DB — `V14__create_ai_style_images.sql`
새 테이블 `findus.missing_person_ai_style_images` (기존 `missing_person_ai_images`의 대표 선택·target_age 의미를 흐리지 않도록 분리):
- `id UUID PK`, `ai_image_id UUID NOT NULL` FK → `missing_person_ai_images(id)` ON DELETE CASCADE, `missing_person_id UUID NOT NULL`
- `sex CHAR(1)` (M|F), `style_key VARCHAR(32)`, `content BYTEA`, `content_type`, `byte_size` (≤10MB, `octet_length` 일치 CHECK — V10 패턴 그대로)
- `options TEXT`(AI 서버 info: ref, scale 등 JSON), `created_at/updated_at/deleted_at`
- 부분 UNIQUE `(ai_image_id, style_key) WHERE deleted_at IS NULL` → 같은 조합 중복 생성 방지, 일괄 생성 재실행 시 건너뛰기 근거
- `(ai_image_id, missing_person_id)` 복합 FK로 다른 아동 연결 차단 (V10의 `fk_ai_images_source_person` 방식, `missing_person_ai_images`에 `UNIQUE(id, missing_person_id)` 추가)
- COMMENT: "AI 생성 결과에 머리 스타일을 입힌 예시. 실제 모습 아님"

## 2. 스타일 목록 (Java 설정)
- `application.yml`에 `app.ai-style.styles.M/F: [{key, name}]` — AI 서버 `configs/hair_presets.yaml`의 basic 스타일과 같은 key.
  AI 서버가 꺼져 있어도 화면 버튼 이름을 보여줄 수 있게 Java가 목록을 가진다.
- 아동 성별 매핑: `MissingPerson.sex`(안전Dream `sexdstnDscd`, "남자"/"여자")를 M/F로 변환하는 작은 유틸. 알 수 없으면 스타일 대상에서 제외.

## 3. 조회 API (공개, 1순위)
`missing` 패키지에 추가. `MissingPersonImageController`·`findPublic` 패턴 재사용.

**아동당 대표 AI 사진(`is_main`) 1장만** 다룬다. 경로에 aiImageId를 받지 않고 서버가 대표를 찾는다.

API 2개: 정보(JSON) 1개 + 사진(PNG) 1개. 스타일 없는 기본 AI 사진과 스타일 사진을 같은 사진 API에서 `style` 파라미터로 구분한다.

| API | 응답 |
|---|---|
| `GET /api/missing-persons/{personId}/ai-image` | `{id, targetAge, createdAt, url, styles:[{key, name, url}]}` — 대표 AI 사진 정보 + 저장된 스타일 목록(성별 목록 순서). `url`은 아래 사진 API 주소. 대표가 없으면 404. 바이트는 읽지 않는 메타데이터 쿼리 |
| `GET /api/missing-persons/{personId}/ai-image/photo?style={key}` | PNG. `style` 없으면 기본 AI 사진, 있으면 그 스타일 사진. 없는 스타일은 404 |

흐름: 상세 화면 진입 → 정보 API 1번 → `<img src=url>`로 기본 AI 사진 표시, `styles`로 버튼 생성 → 버튼 클릭 시 `img.src`를 그 스타일 `url`로 교체(미리 preload).

- 공개 조건: `p.sourceAvailable and p.deletedAt is null and ai.isMain and ai.deletedAt is null and style.deletedAt is null`. 아니면 404.
- 대표가 바뀌면(workbench `selectAiMain`) 이전 대표의 스타일은 자동으로 안 보이게 됨(쿼리가 현재 대표 기준). 새 대표는 일괄 생성을 다시 돌리면 채워짐.
- 응답 헤더: 기존 원본 사진과 같이 `nosniff`, `contentLength`. 버튼 전환 속도를 위해 프론트는 목록을 받은 뒤 스타일 이미지를 미리 로드(preload)하도록 안내.
- `SecurityConfig` GET permitAll에 `/api/missing-persons/*/ai-image`, `/*/ai-image/photo` 추가.
- `MissingResponseAssembler`: 상세 응답에 `aiPhotoUrl`(대표 AI 사진) 정도만 추가(선택).

신규 파일(예): `missing/controller/MissingAiImageController.java`, `missing/dto/MissingAiImageResponse.java`, `missing/entity/MissingPersonAiStyleImage.java`, `missing/repository/MissingPersonAiStyleImage*Repository*.java`(JPA + 인터페이스/Impl, 기존 3파일 패턴), `MissingPersonAiImageJpaRepository`에 공개 조회 쿼리 추가.

## 4. 일괄 생성 (관리자 수동 실행, 2순위)
새 패키지 `aiserver` (커밋 안 된 `aging`에서 이동·정리):
- `AiServerClient` — 기존 코드의 RestClient·`X-API-Key`·`/v1/results/` 경로 검증·PNG 검사·오류 구분(BAD_INPUT/BUSY/UNAVAILABLE) 그대로 쓰고, `age()` 대신 `hair(byte[] png, sex, style)` 추가 (`POST /v1/hair`, `image`+`sex`+`style`, `ref=auto`).
- `AiServerProperties` (`app.ai-server.base-url/api-key/timeout`), `.env.example`에 `AI_SERVER_URL`, `AI_SERVER_API_KEY` 추가. base-url이 비어 있으면 **클라이언트 빈을 만들지 않고** 일괄 생성만 비활성(앱 시작은 정상) — 지금 aging 코드의 "시작 실패" 문제 해소.

`AiStyleGenerationService` + `/api/admin/ai-styles`(ADMIN, 기존 `/api/admin/**` 규칙):
- `POST /api/admin/ai-styles/generate` (옵션 `personId`) → 202, 백그라운드 단일 스레드로 실행. 이미 실행 중이면 409.
- `GET /api/admin/ai-styles/generate` → 진행 상태(대상 수, 완료, 건너뜀, 실패, 현재 항목).
- 대상: 공개 중 아동의 **대표 AI 이미지 1장** × 성별 스타일 중 **아직 없는 것만**. 재실행해도 이어서 진행.
- 한 장씩 순서대로 호출(GPU 1장) → 머리 모델이 올라간 채 유지돼 2장째부터 빨라짐. BUSY면 잠깐 쉬고 재시도, BAD_INPUT(얼굴 못 찾음)은 기록 후 다음.
- 저장은 건별 트랜잭션. 동시 저장 충돌(UNIQUE 위반)은 건너뜀으로 처리.
- 패턴 참고: `missing/controller/MissingBatchController.java`, `MissingBatchRunner`.

## 5. workbench 수동 생성·저장 (3순위, AI 서버 수정 포함)
- Java: `/api/local/aiworkbench/style-saves` (기존 `AiWorkbenchController`·`LocalServerOnlyFilter` 체인 안) — `personId, styleKey, image` 저장(대표 AI 이미지에 연결), 같은 조합이 있으면 교체(이전 것 soft delete) 또는 409 중 택1(교체 권장: 수동 저장은 "더 나은 것으로 바꾸기" 용도).
- AI 서버(`ai_server_final/app/main.py`, `java_client.py`): 저장된 AI 이미지를 불러와 `/v1/hair` 적용 → "스타일로 저장" 버튼. `java_client.ai_image()` 재사용. 공개 조회 API가 생기면 그것을 써도 됨.

## 6. 정리
- 커밋 안 된 `aging/controller/*`, `aging/dto`, `aging/service`, `local-preview/aging-preview.html`, `AgingServiceTests` 삭제. `AiServerClientTests`는 `aiserver`로 옮겨 hair 기준으로 수정.
- `ApiExceptionHandler`에 `AiServerException` 매핑(관리자 API용).
- `application.yml`/`application-local.yml`의 sslmode·`FINDUS_ENV_FILE` 변경은 별개 작업이므로 유지(별도 커밋 권장).

## 속도 메모
- 사용자 클릭 시: DB 조회 + 바이트 전송뿐 (생성 없음). 프론트 preload로 체감 즉시.
- 생성 시: 순차 실행으로 모델 재적재(약 17초) 회피. 추가로 AI 서버 `/v1/hair`에 `aligned=true`(이미 1024 정렬된 입력이면 정렬 생략) 옵션을 넣으면 더 단축 가능 — 선택 사항.
- 저장 용량: PNG 1024² 약 1~2MB × 아동당 최대 5장. 필요하면 나중에 JPEG 변환 검토.

## 검증
1. `./gradlew test` — 신규 테스트:
   - 조회: 대표 없음/삭제/공개 중단/없는 styleKey → 404, 대표가 아닌 AI 이미지의 스타일은 안 보임, 목록에 성별 스타일만 포함 (`MissingImageMigrationTests` 등 기존 DB 테스트 방식 참고)
   - 마이그레이션: 중복 `(ai_image_id, style_key)` 거절, 다른 아동 연결 거절
   - 일괄 생성: 가짜 HTTP 서버(`AiServerClientTests`의 `HttpServer` 방식)로 남 3장/여 5장 생성, 재실행 시 0장, 400은 실패로 기록 후 계속, 503 재시도
2. 실제 연결: AI 서버 `start_server.bat` → Java local 실행 → workbench로 AI 이미지 1개 저장 → `POST /api/admin/ai-styles/generate?personId=...` → 상태 API로 완료 확인 → `GET .../ai-image`에 styles 3/5개, 각 URL이 PNG로 열리는지 확인.
