# feature/ai-query 변경 사항

대상 커밋: `86ae372`, `5fbe8c6`, `c6c9e0f` (기준: `690146d`)

## 요약

- 원격 DB 연결을 위해 **PostgreSQL SSL 모드**와 **실행 시 읽을 환경 파일**을 고를 수 있게 했습니다.
- 아동별 **대표 AI 사진 조회 API**(비회원 공개)를 추가했습니다.
- 대표 AI 사진에 아동 성별에 맞는 **머리 스타일을 미리 만들어 저장하는 관리자 API**를 추가했습니다. 생성은 외부 AI 서버(`ai_server_final`)에 요청합니다.
- AI 스타일 기능의 계획 문서와 사용법 문서를 추가했습니다.

---

## 1. DB SSL 모드와 환경 파일 선택 (`86ae372`)

| 항목 | 변경 |
| --- | --- |
| `application.yml` | JDBC URL에 `?sslmode=${POSTGRES_SSLMODE:prefer}` 추가 |
| `application-local.yml` | `.env` 대신 `${FINDUS_ENV_FILE:.env}`를 읽음 |
| `.env.example` | `POSTGRES_SSLMODE`, `FINDUS_ENV_FILE` 설명 추가 |

- Supabase 같은 원격 DB는 `POSTGRES_SSLMODE=require`로 지정합니다.
- `FINDUS_ENV_FILE`은 `.env` 안이 아니라 **실행 환경변수**로 지정합니다.
  ```powershell
  $env:FINDUS_ENV_FILE=".env.deploy"   # PowerShell
  ```
  ```bash
  FINDUS_ENV_FILE=.env.deploy          # bash
  ```

## 2. 대표 AI 사진 조회와 머리 스타일 사전 생성 (`5fbe8c6`)

### 2-1. 새 API

| 메서드 | 경로 | 권한 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/missing-persons/{personId}/ai-image` | 공개 | 대표 AI 사진 정보 + 저장된 스타일 목록. 없으면 404 `AI_IMAGE_NOT_FOUND` |
| GET | `/api/missing-persons/{personId}/ai-image/photo?style={key}` | 공개 | `style`이 없으면 대표 AI 사진, 있으면 해당 스타일 사진(PNG/JPEG). 없으면 404 |
| POST | `/api/admin/ai-styles/generate?personId={id}` | ADMIN | 머리 스타일 일괄 생성 시작. 202 반환, 이미 실행 중이면 409 `AI_STYLE_RUNNING` |
| GET | `/api/admin/ai-styles/generate` | ADMIN | 일괄 생성 진행 상태 |

정보 API 응답 예:

```json
{
  "id": "…",
  "targetAge": 30,
  "createdAt": "…",
  "url": "/api/missing-persons/{id}/ai-image/photo",
  "styles": [
    { "key": "bob", "name": "단발", "url": "/api/missing-persons/{id}/ai-image/photo?style=bob" }
  ],
  "notice": "AI가 만든 예상 모습이며 실제 모습이 아닙니다."
}
```

- 사진 API는 `Cache-Control: private, max-age=600`으로 응답해 스타일 버튼 전환이 바로 되도록 했습니다. 대표가 바뀌어도 최대 10분 안에 새 사진을 받습니다.
- `styles`에는 **아동 성별에 맞고, 현재 설정 목록에 있고, 실제로 저장된** 스타일만 설정 순서대로 들어갑니다.
- 스타일 사진은 **현재 대표 AI 사진 기준**으로만 찾으므로, 대표가 바뀌면 이전 대표의 스타일은 보이지 않습니다.

### 2-2. 기존 응답 변경

- `MissingPersonResponse`에 `aiPhotoUrl` 필드를 추가했습니다. 대표 AI 사진이 있으면 `/api/missing-persons/{id}/ai-image/photo`, 없으면 `null`입니다.
- 목록 조회 시 아동 id 목록으로 대표 AI 사진 여부를 **한 번에** 조회하며 사진 바이트는 읽지 않습니다.
- `SecurityConfig`: 위 두 공개 GET 경로를 `permitAll`에 추가했습니다.

### 2-3. 머리 스타일 일괄 생성 동작

- 대상: `personId`가 없으면 공개 중인 모든 아동의 대표 AI 사진, 있으면 그 아동만.
- 이미 저장된 스타일은 빼고 계획하므로 **다시 실행하면 남은 것만 이어서** 만듭니다.
- GPU가 한 장이라 단일 스레드로 **한 장씩 순서대로** 요청합니다(1장 약 40~80초).
- 성별은 원본 문자열(`남자`/`여자`, `M`/`F`)에서 판별하며, 알 수 없으면 대상에서 제외합니다.
- AI 서버 응답별 처리:

| 응답 | 처리 |
| --- | --- |
| 400 (얼굴 미검출 등) | 실패로 기록, **같은 사진의 남은 스타일은 요청하지 않고 건너뜀** |
| 503 (다른 변환 처리 중) | `busy-retry-delay`(30초) 쉬고 최대 `busy-retries`(3회) 재시도 후 실패 기록 |
| 연결 실패·기타 오류 | 전체 실행을 중단하고 상태를 `FAILED`로 남김 |
| 저장 시 중복(유니크 위반) | 건너뜀(`skipped`) |

- 진행 상태: `state`(IDLE/RUNNING/DONE/FAILED), `total`, `created`, `skipped`, `failed`, `current`, `errors`(최근 20개).

### 2-4. 기본 스타일 목록 (`app.ai-style`)

| 성별 | key / 이름 |
| --- | --- |
| 남 | `buzz` 아주 짧은 머리, `short` 짧은 머리, `short_bang` 덮머 |
| 여 | `short` 숏컷, `bob` 단발, `bob_bang` 앞머리 단발, `long` 긴 머리, `long_bang` 앞머리 긴 머리 |

key는 AI 서버 `configs/hair_presets.yaml`과 같아야 합니다. AI 서버가 꺼져 있어도 버튼 이름을 보여줄 수 있도록 목록은 백엔드 설정에 둡니다.

### 2-5. AI 서버 클라이언트 (`aiserver` 패키지)

- `AiServerClient`: `POST /v1/hair`(multipart: `image`, `sex`, `style`, `ref=auto`) 호출 후 결과 PNG를 내려받습니다.
- 결과 주소는 같은 AI 서버의 `/v1/results/` 경로만 따라가며(`..`, `//` 거부), PNG 시그니처와 20MB 상한을 확인합니다.
- 실패는 `AiServerException`의 `Reason`(`BAD_INPUT` / `BUSY` / `UNAVAILABLE`)으로 구분합니다. 메시지에 사진이나 API 키는 넣지 않습니다.
- 설정(`app.ai-server`): `base-url`(기본 `http://127.0.0.1:8000`), `api-key`(설정 시 `X-API-Key` 헤더), `timeout`(기본 5분).

### 2-6. DB 마이그레이션 `V14__create_ai_style_images.sql`

- 새 테이블 `findus.missing_person_ai_style_images` (AI 사진 테이블과 분리해 대표 선택·목표 나이 의미가 섞이지 않게 함).
- `missing_person_ai_images`에 `UNIQUE (id, missing_person_id)` 추가 → 복합 FK로 **다른 아동의 AI 사진 연결을 DB에서 차단**, AI 사진 삭제 시 `CASCADE`.
- CHECK 제약: `sex IN ('M','F')`, `style_key ~ '^[a-z0-9_]{1,32}$'`, `content_type`은 JPEG/PNG, 크기 0 초과 10MB 이하.
- 부분 유니크 인덱스 `(ai_image_id, style_key) WHERE deleted_at IS NULL` → 같은 사진의 같은 스타일은 하나만 유지.
- `options` 컬럼에 AI 서버 생성 정보(결과 id, 소요 시간, 참조 사진·배율 등)를 JSON 문자열로 저장.

### 2-7. 새 환경변수

```dotenv
AI_SERVER_URL=http://127.0.0.1:8000
AI_SERVER_API_KEY=        # AI 서버에 API_KEY를 정했을 때만
AI_SERVER_TIMEOUT=5m      # 선택
```

### 2-8. 테스트

- `AiServerClientTests` (가짜 HTTP 서버): 정상 PNG 수신, 400 안내 전달, 503 → BUSY, 결과 경로 외 주소 거부, 서버 꺼짐 → UNAVAILABLE.
- `AiStyleTests` (Testcontainers): DB 중복·교차 아동 연결 거절, 성별에 맞는 저장된 스타일만 비회원에게 노출, 아동 정보의 `aiPhotoUrl`, 대표 없음/공개 중단 404, 재실행 시 남은 것만 생성, 얼굴 미검출 시 남은 스타일 건너뜀, BUSY 재시도 후 실패, 관리자 전용 권한.
- `SwaggerTests`, `FindUsApplicationTests`, `MemberFavoriteTests`: 새 API·테이블·응답 필드에 맞게 수정.

## 3. 문서 (`c6c9e0f`)

- [docs/ai-style-plan.md](ai-style-plan.md): AI 스타일 이미지 기능 계획
- [docs/ai-style-images.md](ai-style-images.md): AI 서버 실행, 일괄 생성, 화면 연동 사용법

## 배포 시 확인할 점

- Flyway `V14` 마이그레이션이 적용됩니다.
- 원격 DB를 쓰면 `POSTGRES_SSLMODE=require`를 지정합니다.
- 스타일 생성이 필요한 환경에만 AI 서버를 띄우고 `AI_SERVER_URL`(필요 시 `AI_SERVER_API_KEY`)을 설정합니다. 조회 API는 AI 서버 없이도 동작합니다.
- 일괄 생성 진행 상태는 메모리에만 있으므로 서버를 재시작하면 초기화됩니다. 다시 실행하면 남은 스타일만 이어서 만듭니다.
