# AI 이미지 조회 · 머리 스타일 미리보기

작성일: 2026-10-26
대상: 프론트·백엔드·AI 서버 담당 팀원

## 한 줄 요약

실종아동 **대표 AI 사진(나이 변환 결과) 1장**에 성별에 맞는 머리 스타일을 **미리 만들어 DB에 저장**해 둡니다.
사용자가 스타일 버튼을 누르면 새로 생성하지 않고 저장된 사진만 바꿔 보여줍니다.

- 머리 변환은 1장에 40~80초 걸리고 GPU가 1장뿐이라 실시간 생성은 하지 않습니다.
- 나이 변환(AI 사진 만들기)은 지금처럼 AI 서버 workbench에서 **수동으로** 합니다. 자동 생성은 없습니다.
- AI 사진이 있는 아동만 스타일 대상입니다.

## 전체 흐름

```
[사전 작업 · 관리자]
 workbench(/test/workbench)에서 나이 변환 → 결과 저장
        ↓ missing_person_ai_images (아동별 대표 1장 is_main)
 POST /api/admin/ai-styles/generate
        ↓ 백엔드가 AI 서버 POST /v1/hair를 한 장씩 호출
 missing_person_ai_style_images 에 저장 (남 3장 / 여 5장)

[사용자 화면]
 GET /api/missing-persons/{id}/ai-image        → 사진 주소 + 스타일 버튼 목록
 <img src=url>                                 → 기본 AI 사진
 버튼 클릭 → img.src = styles[i].url           → 저장된 스타일 사진 (생성 없음)
```

## 스타일 목록 (아동 성별 기준)

| 성별 | key → 버튼 이름 |
|---|---|
| 남 (`남자`) | `buzz` 아주 짧은 머리 · `short` 짧은 머리 · `short_bang` 덮머 |
| 여 (`여자`) | `short` 숏컷 · `bob` 단발 · `bob_bang` 앞머리 단발 · `long` 긴 머리 · `long_bang` 앞머리 긴 머리 |

- 목록은 백엔드 `application.yml`의 `app.ai-style.male / female`에 있습니다. key는 AI 서버 `configs/hair_presets.yaml`의 기본 스타일과 같아야 합니다.
- 색은 바꾸지 않습니다(AI 서버가 고른 참조 사진 색 그대로).
- 성별을 알 수 없는 아동은 스타일을 만들지 않습니다.

## 프론트용 API (비회원 공개)

### 1. 대표 AI 사진 정보 — `GET /api/missing-persons/{personId}/ai-image`

```json
{
  "id": "7c1e…",
  "targetAge": 30,
  "createdAt": "2026-10-26T01:20:00",
  "url": "/api/missing-persons/{personId}/ai-image/photo",
  "styles": [
    { "key": "bob",  "name": "단발",   "url": "/api/missing-persons/{personId}/ai-image/photo?style=bob" },
    { "key": "long", "name": "긴 머리", "url": "/api/missing-persons/{personId}/ai-image/photo?style=long" }
  ],
  "notice": "AI가 만든 예상 모습이며 실제 모습이 아닙니다."
}
```

- `styles`에는 **저장이 끝난 스타일만** 설정 목록 순서대로 들어갑니다. 비어 있을 수 있습니다(아직 생성 전).
- 대표 AI 사진이 없거나 공개 중단된 아동은 `404` `{"code":"AI_IMAGE_NOT_FOUND"}`. 이 경우 AI 사진 영역을 숨겨 주세요.
- `notice` 문구는 사진 근처에 꼭 표시해 주세요.

### 2. 사진 — `GET /api/missing-persons/{personId}/ai-image/photo[?style={key}]`

- `style`이 없으면 기본 AI 사진, 있으면 그 스타일 사진(PNG 1024×1024).
- 저장되지 않은 스타일은 `404`.
- `Cache-Control: private, max-age=600`이라 한 번 받은 사진은 10분 동안 브라우저가 다시 받지 않습니다.

### 화면 예시

```js
const res = await fetch(`/api/missing-persons/${personId}/ai-image`);
if (res.status === 404) return hideAiSection();
const ai = await res.json();

img.src = ai.url;
// 버튼을 누르는 순간 바로 바뀌도록 미리 받아 둡니다.
ai.styles.forEach(s => { new Image().src = s.url; });

renderButtons([{ name: '기본', url: ai.url }, ...ai.styles], s => { img.src = s.url; });
```

### 프론트 반영 (findus/frontend)

| 파일 | 내용 |
|---|---|
| `lib/missingApi.ts` | `fetchMissingAiImage(id)`: 주소 검증 후 `/backend` 프록시 주소로 바꿈. 404·401·서버 오류·연결 실패는 모두 `undefined`(상세 페이지를 깨지 않음). 다른 아동 경로·잘못된 style key는 버림. `withAiImage(person, ai)`: `photos.aged`, `photos.styles`, `aiNote`, `aiTargetAge` 채움 |
| `lib/data.ts` | `getMissingPersonWithAiImage(id)`: 아동 정보 + 대표 AI 사진 |
| `app/missing/[id]/page.tsx` | 본문만 위 함수를 씀. 메타데이터·공유 이미지(og/story)는 그대로 |
| `components/AiStylePhoto.tsx` | 기본 AI 사진 + `StyleTabs` 스타일 버튼 + 안내 문구. 스타일 사진은 미리 받아 둠 |
| `components/PersonDetailBody.tsx` | 백엔드 데이터이고 `photos.aged`가 있을 때만 `AiStylePhoto` 표시 |

- 목록·상세 응답(`MissingPersonResponse`)에 `aiPhotoUrl`(대표 AI 사진 주소, 없으면 null)을 추가했습니다. 프론트 `toMissingPerson`이 이 아동의 경로일 때만 `photos.aged`로 씁니다. 그래서 홈·검색 카드와 모바일 비교 화면에도 기본 AI 사진이 나옵니다. 스타일 버튼은 상세 페이지에만 있습니다.
- **정렬:** `lib/filter.ts`의 `sortPersons`는 AI 예상 모습이 있는 아동을 먼저 두고, 그 안에서 사용자가 고른 정렬(최신순·오래된순·이름순)을 따릅니다. 홈, 검색, 마이페이지 목록이 모두 이 함수를 거칩니다.
- 공유 이미지(og/story)는 서버에서 `/backend/...` 상대 주소를 받지 못하는 기존 구조라 이번에 바꾸지 않았습니다.

## 관리자용 API — 스타일 일괄 생성

| API | 설명 |
|---|---|
| `POST /api/admin/ai-styles/generate[?personId=]` | 시작. 바로 `202`와 진행 상태를 돌려주고 뒤에서 계속 만듭니다. 이미 실행 중이면 `409` |
| `GET /api/admin/ai-styles/generate` | 진행 상태 `{state, total, created, skipped, failed, current, errors}` |

- ADMIN 권한과 CSRF 토큰이 필요합니다(기존 `/api/admin/**` 규칙).
- `personId`를 비우면 대표 AI 사진이 있는 **모든 공개 아동**이 대상입니다.
- **아직 없는 스타일만** 만듭니다. 중간에 끊겨도 다시 실행하면 남은 것부터 이어서 만듭니다.
- `state`: `RUNNING` → `DONE`. AI 서버에 연결이 안 되면 그 자리에서 멈추고 `FAILED`가 됩니다.
- 실패 처리:
  - 얼굴을 찾지 못함(AI 서버 400): 그 사진의 나머지 스타일은 요청하지 않고 `failed`로 기록합니다.
  - AI 서버가 바쁨(503): 30초 쉬고 최대 3번 다시 요청합니다(`app.ai-style.busy-retry-delay`, `busy-retries`).
- 소요 시간: 1장 약 40~80초. 남아 10명이면 3장 × 10 = 약 20~40분입니다.

### 실행 순서

1. AI 서버 PC에서 `ai_server_final/start_server.bat` 실행
2. 백엔드 `.env`의 `AI_SERVER_URL`(기본 `http://127.0.0.1:8000`) 확인. AI 서버에 `API_KEY`를 정했다면 `AI_SERVER_API_KEY`에 같은 값
3. workbench에서 대표 AI 사진 저장
4. 관리자로 로그인해 Swagger에서 `POST /api/admin/ai-styles/generate` 실행 → `GET`으로 `DONE` 확인
5. `GET /api/missing-persons/{id}/ai-image`의 `styles`에 남 3개 / 여 5개가 나오는지 확인

## DB — `V14__create_ai_style_images.sql`

새 테이블 `findus.missing_person_ai_style_images`

| 컬럼 | 설명 |
|---|---|
| `ai_image_id`, `missing_person_id` | 스타일을 입힌 AI 사진과 그 아동. 복합 FK로 다른 아동의 AI 사진과 연결되지 않게 막습니다 |
| `sex`, `style_key` | `M`/`F`, 스타일 key |
| `content`, `content_type`, `byte_size` | 결과 PNG (10MB 이하) |
| `options` | AI 서버 생성 정보 JSON (`resultId`, `seconds`, 참조 사진 등) |
| `deleted_at` | 소프트 삭제 |

- `(ai_image_id, style_key)`는 활성 행 기준으로 하나만 허용합니다(부분 UNIQUE).
- AI 사진 행이 삭제되면 스타일도 함께 지워집니다(`ON DELETE CASCADE`).
- **대표 AI 사진을 바꾸면** 이전 대표의 스타일은 조회에서 빠집니다(현재 대표 기준으로만 조회). 새 대표의 스타일은 일괄 생성을 다시 실행하면 채워집니다.
- `missing_person_ai_images`에 `UNIQUE(id, missing_person_id)`를 추가했습니다(복합 FK용).

## 코드 위치

| 역할 | 파일 |
|---|---|
| 공개 조회 API | `missing/controller/MissingAiImageController.java` |
| 조회 서비스 | `missing/service/MissingAiImageQueryServiceImpl.java` |
| 스타일 엔티티·저장소 | `missing/entity/MissingPersonAiStyleImage.java`, `missing/repository/MissingPersonAiStyleImage*` |
| 대표 AI 사진 조회 쿼리 | `missing/repository/MissingPersonAiImageJpaRepository.java` |
| 스타일 저장 | `MissingImageManagementService.saveAiStyle()` |
| 스타일 목록·성별 변환 | `aistyle/config/AiStyleCatalog.java`, `AiStyleProperties.java` |
| 일괄 생성 | `aistyle/service/AiStyleGenerationService.java`, `aistyle/controller/AiStyleAdminController.java` |
| AI 서버 호출 | `aiserver/client/AiServerClient.java` (`POST /v1/hair`) |

## 이번에 정리한 것

- 커밋 전이던 `/api/aging`(사용자 사진 업로드 나이 변환)과 시험 페이지 `/test/aging`은 **삭제**했습니다. AI 서버 호출 코드만 `aiserver` 패키지로 옮겨 머리 변환용으로 고쳤습니다.
- 설정 이름: `app.aging.*` → `app.ai-server.*` (환경 변수 `AI_SERVER_URL`, `AI_SERVER_API_KEY`, `AI_SERVER_TIMEOUT`은 그대로).
- 함께 넣었던 업로드 크기 설정과 업로드 초과 예외 처리도 `/api/aging` 전용이라 뺐습니다.

## 아직 안 한 것

- **workbench에서 스타일을 직접 골라 저장하는 기능**: 계획에는 있었지만 AI 서버(`ai_server_final`)도 함께 고쳐야 해서 이번에는 넣지 않았습니다. 지금은 관리자 일괄 생성으로만 만들어집니다.
- 마음에 들지 않는 스타일 사진을 지우거나 다시 만드는 관리자 API.
- AI 서버 `/v1/hair`의 `aligned=true` 옵션(이미 정렬된 1024 사진이면 얼굴 정렬을 건너뛰어 더 빨라짐).

## 테스트

- `AiStyleTests` (PostgreSQL·Redis 컨테이너 + 가짜 AI 서버)
  - DB가 같은 스타일 중복과 다른 아동 연결을 거절하는지
  - 성별에 맞는 저장된 스타일만 목록 순서대로 나오는지, 대표가 아닌 AI 사진의 스타일은 안 보이는지
  - 대표 없음·공개 중단 → 404
  - 일괄 생성: 남 3장 / 여 4장(1장은 이미 있음) 생성, 다시 실행하면 0장, 400이면 남은 스타일 건너뜀, 503이면 재시도 후 실패
  - 일괄 생성 API는 비회원이 쓸 수 없음
- `AiServerClientTests`: `/v1/hair` 요청 형식, 400/503/서버 꺼짐, 결과 주소 검증
- 기존 테스트 수정
  - `FindUsApplicationTests`: 최신 마이그레이션 버전 기대값 13 → 14
  - `SwaggerTests` 기준 문서 비교: 새 API·스키마·태그는 제외하고, 컨트롤러가 늘면서 바뀐 **태그 나열 순서**는 정렬한 뒤 비교 (태그 내용은 그대로 비교)
  - `SwaggerTests`: `MissingPersonResponse.aiPhotoUrl`도 기준 문서 비교에서 제외
  - `MemberFavoriteTests` 쿼리 수 상한 4 → 5: 대표 AI 사진 여부를 한 번에 묻는 쿼리 1개 추가(아동 수와 무관, N+1 아님)
- 전체 `./gradlew test` 197개 통과, 프론트 `npm test` 126개 통과 (2026-10-26)
