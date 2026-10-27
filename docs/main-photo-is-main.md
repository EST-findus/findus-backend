# 대표 사진(`is_main`) 지정 작업

대상 커밋: `8555e6e` (대표 원본 사진 수작업 지정 화면), 기반 마이그레이션 `V10__add_main_and_ai_images.sql`

## 요약

- 안전Dream(경찰청) API가 주는 사진은 **아동 본인 사진이 아닌 경우가 많고**, 한 아동에 여러 장이 섞여 옵니다.
- 그래서 "첫 번째 사진 = 대표 사진"으로 쓰면 목록·상세·AI 변환에 엉뚱한 사진이 쓰일 수 있습니다.
- 백엔드에서 아동별 대표 사진을 `is_main` 컬럼으로 관리하고, **사람이 직접 대표를 고르는 로컬 작업 화면**을 추가했습니다.

---

## 1. 안전Dream API 사진의 문제점

한 아동(`msspsnIdntfccd`)에 대해 상세 페이지에서 여러 장의 사진을 받아오는데, 내용이 일정하지 않습니다.

| 경우 | 설명 | 문제 |
| --- | --- | --- |
| 부모 사진만 있음 | 아동 사진 없이 부모(가족) 사진만 올라온 경우 | 아동 얼굴이 아니므로 AI 나이 변환·카드 대표 이미지로 쓰면 안 됨 |
| 아동 사진이 없음 | 사진 자체가 없거나 아동 외 사진만 있는 경우 | 대표로 쓸 사진이 없음 |
| 다양한 종류가 섞임 | 실제 아동 사진, 몽타주, 이미 AI로 나이 변환된 사진, 부모 사진 등이 함께 있음 | 순서가 일정하지 않아 첫 번째 사진이 실제 아동 사진이라는 보장이 없음 |

- API 응답에는 사진 종류(실사/몽타주/AI 변환/가족)를 구분하는 값이 없습니다. 자동으로 고를 기준이 없으므로 **사람의 판단**이 필요합니다.
- 대표 사진은 목록 카드, 상세 화면, AI 나이 변환의 입력(`source_image_id`)에 쓰이므로 잘못 고르면 결과 전체가 틀어집니다.

## 2. DB: `is_main` 컬럼

### 2-1. `missing_person_images.is_main` (원본 사진)

- `BOOLEAN NOT NULL DEFAULT FALSE`
- 마이그레이션 시 아동별로 **순번(`sort_order`)이 가장 작은 활성 사진**을 대표로 지정했습니다.
- 제약 조건
  - `uk_missing_images_main`: 부분 UNIQUE 인덱스 `(missing_person_id) WHERE is_main AND deleted_at IS NULL` → **아동당 활성 대표는 최대 1장**
  - `ck_missing_images_main_active`: 삭제된 사진은 대표가 될 수 없음

### 2-2. `missing_person_ai_images.is_main` (AI 생성 사진)

- 같은 방식으로 아동당 대표 AI 사진 1장을 관리합니다. 최초 저장된 AI 사진이 기본 대표입니다.
- `fk_ai_images_source_person`: AI 사진의 입력 원본은 **같은 아동의 원본 사진만** 연결되도록 DB에서 막습니다.

## 3. 대표 사진 유지 규칙 (`MissingImageManagementServiceImpl`)

| 메서드 | 동작 |
| --- | --- |
| `ensureOriginalMain` | 활성 대표가 없을 때만 첫 순번 활성 사진을 대표로 지정. **이미 대표가 있으면 건드리지 않음** |
| `selectOriginalMain` | 사람이 고른 사진으로 대표 변경. 이전 대표 해제를 먼저 flush해 부분 UNIQUE 인덱스 충돌을 피함 |
| `selectAiMain` / `deleteAiResult` | 대표 AI 사진 변경, 삭제 시 남은 AI 사진 중 가장 먼저 만든 것을 대표로 지정 |

경찰청 동기화(`MissingSyncServiceImpl`)와의 관계:

- 동기화가 끝날 때마다 `ensureOriginalMain`을 호출하므로 **사람이 지정한 대표는 동기화 후에도 유지**됩니다.
- 지정한 사진이 경찰청에서 사라져 soft delete되면 대표도 해제되고, 첫 활성 사진으로 다시 지정됩니다.
- 상세 조회가 실패해 대표 사진 1장만 받은 경우(`API_FALLBACK`)에는 기존 다중 사진을 덮어쓰지 않습니다.

공개 API 반영(`MissingResponseAssembler`):

- 응답의 대표 사진 URL은 `is_main = true`인 사진입니다.
- 대표가 없으면 첫 번째 사진, 사진이 아예 없으면 경찰청 API의 사진 URL을 씁니다.
- AI 사진 정보(`GET /api/missing-persons/{id}/ai-image`)의 `images`는 그 아동의 활성 AI 사진 전체입니다. 대표 AI 사진이 첫 항목이고 나머지는 만든 순서이며, 각 사진은 `/ai-image/photos/{aiImageId}`로 받습니다.
- 대표 AI 사진(`is_main`)이 지정되지 않은 아동도 활성 AI 사진이 있으면 응답합니다. 이때는 가장 먼저 만든 사진이 대표 자리(`url`, `/ai-image/photo`, `images[0]`)에 오고, 스타일 사진은 보여주지 않습니다.

## 4. 대표 원본 사진 지정 화면 (`8555e6e`)

사람이 아동별 사진을 보고 실제 아동 사진을 대표로 고르는 작업 화면입니다. 사진을 누르면 연결된 DB에 바로 저장됩니다.

### 4-1. 실행

```powershell
# 원격 DB에 반영할 때 (.env.deploy의 POSTGRES_* · POSTGRES_SSLMODE=require 사용)
$env:FINDUS_ENV_FILE=".env.deploy"; $env:MAIN_PHOTO_TOOL_ENABLED="true"; ./gradlew bootRun
```

접속: http://localhost:18080/test/main-photo

### 4-2. API (화면 전용, Swagger 미노출)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/test/main-photo` | 작업 화면 HTML |
| GET | `/api/local/main-photo/persons?page=0&size=10&multiOnly=true` | 공개 아동 목록 + 사진별 대표 여부. `multiOnly=true`면 활성 사진 2장 이상인 아동만 |
| PUT | `/api/local/main-photo/persons/{personId}/main` | body `{ "imageId": "…" }`로 대표 변경 (204) |

- 응답의 `dbHost`(host:port만)로 **지금 어느 DB를 고치는지** 화면 맨 위에 표시합니다.
- 목록 순서는 공개 목록과 같은 `occurrenceDate DESC, id DESC`로 고정해 검토 중 페이지가 흔들리지 않습니다.
- ‘검토 완료’ 체크는 DB가 아닌 브라우저 localStorage에만 저장합니다.

### 4-3. 보안

- `local` 프로필 + `MAIN_PHOTO_TOOL_ENABLED=true`일 때만 빈이 등록됩니다. 기본값은 `false`입니다.
- `MainPhotoToolSecurityConfig`로 별도 SecurityFilterChain을 두어 기존 회원 인증 규칙은 바꾸지 않았습니다.
- `SameMachineFilter`
  - 루프백 주소 + `localhost` Host로 접속한 요청만 허용 (DNS 리바인딩 차단)
  - `Sec-Fetch-Site`가 다른 사이트면 차단
  - 변경 요청은 `X-Findus-Tool: main-photo` 헤더가 있어야 함 (다른 사이트에서 보낸 요청 차단)

### 4-4. 설정

| 항목 | 변경 |
| --- | --- |
| `application-local.yml` | `app.main-photo-tool.enabled: ${MAIN_PHOTO_TOOL_ENABLED:false}` |
| `.env.example` | `MAIN_PHOTO_TOOL_ENABLED=false` 설명 추가 |

### 4-5. 테스트 (`MissingMainPhotoToolTests`)

- 화면과 목록이 사진 2장 이상 아동과 대표 여부를 보여준다
- 사진을 고르면 대표가 한 장만 바뀌고 공개 API에 반영된다
- 다른 사이트·다른 PC·도구 헤더 없는 변경 요청은 막는다

## 5. 작업 시 확인할 점

- 화면 맨 위 ‘연결된 DB’가 의도한 DB인지 먼저 확인합니다. 연습은 `FINDUS_ENV_FILE` 없이 로컬 DB로 합니다.
- 원격 DB로 실행할 때는 `DUMMY_DATA_ENABLED=false`(또는 `DUMMY_STARTUP_ENABLED=false`)여야 더미 데이터를 원격에 쓰지 않습니다.
- 대표 사진을 바꾸면 이후 만드는 AI 나이 변환의 입력도 바뀝니다. 이미 만든 AI 사진은 그대로이므로 필요하면 다시 생성합니다.
- 아동 사진이 아예 없는 경우(부모 사진·몽타주만 있음)는 이 화면에서 "대표 없음"으로 지정할 수 없습니다. 현재는 그나마 가까운 사진을 고르는 방식입니다.
- 작업이 끝나면 백엔드를 종료합니다.
