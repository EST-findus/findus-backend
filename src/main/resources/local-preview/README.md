# 실종아동 로컬 DB 확인 페이지

Docker와 백엔드를 실행하고 아래 주소로 접속합니다.

http://localhost:18080/test/safe182

- 백엔드는 `local` 프로필로 실행합니다. 페이지는 다른 프로필에서 제공하지 않습니다.
- 회원가입·로그인·JWT·CSRF 준비 없이 페이지와 동기화를 사용할 수 있습니다.
- 최초 접속·DB 새로고침·페이지 이동은 우리 DB 조회 API만 호출합니다.
- ‘경찰청 동기화 → DB 저장’ 버튼은 실제 수집 API를 호출하며 완료 후 DB를 다시 조회합니다.
- `curl -X POST http://localhost:18080/api/admin/missing-sync`로 따로 실행했다면 완료 후 ‘DB 새로고침’을 누릅니다.
- `.env`의 `SAFE182_ESNTL_ID`, `SAFE182_AUTH_KEY`가 필요합니다. 키는 화면에 넣지 않습니다.
- 전체 번호를 표시하고 한 페이지당 10명, 페이지 번호는 10개씩 표시합니다.
- 사진은 `missing_person_images`의 실제 원본을 백엔드에서 조회하여 모두 표시합니다.
- 사진 없음과 사진 조회 실패를 구분합니다. 부분 수집 실패 건수도 동기화 결과에 표시합니다.

`safe182-preview.html`은 이제 실제 데이터가 없는 화면 코드입니다. `file://`로 열면 로컬 서버 주소를 안내합니다. HTML·JSON에 경찰청 데이터를 넣던 생성 도구와 기존 JSON 결과는 제거했습니다. 기존 `./gradlew safeDreamPreview` 명령은 새 주소만 안내하며 외부 요청이나 파일 생성을 하지 않습니다.

페이지 응답과 사진 응답은 캐시 저장을 금지하고, 데이터 요청도 `cache: no-store`로 조회합니다. 화면을 열 때마다 DB의 현재 공개 데이터가 반영됩니다. 화면 코드는 Git으로 공유하지만 개인정보·사진을 넣은 파일은 공유하지 않습니다.

## 대표 원본 사진 지정 화면

아동별 원본 사진 중 대표(`missing_person_images.is_main`)를 사람이 직접 고르는 화면입니다. 사진을 누르면 연결된 DB에 바로 저장됩니다.

```powershell
# 원격 DB에 반영할 때 (.env.deploy의 POSTGRES_* · POSTGRES_SSLMODE=require 사용)
$env:FINDUS_ENV_FILE=".env.deploy"; $env:MAIN_PHOTO_TOOL_ENABLED="true"; ./gradlew bootRun
```

http://localhost:18080/test/main-photo

- 화면 맨 위 ‘연결된 DB’가 의도한 DB 주소인지 먼저 확인합니다. 로컬 DB로 연습하려면 `FINDUS_ENV_FILE` 없이 실행합니다.
- 원격 DB로 실행할 때는 `DUMMY_DATA_ENABLED=false`(또는 `DUMMY_STARTUP_ENABLED=false`)여야 시작 시 더미 데이터를 원격에 쓰지 않습니다.
- 기본은 활성 사진이 2장 이상인 아동만 보여줍니다. 대표 변경은 기존 `selectOriginalMain`을 사용하므로 아동당 대표는 항상 1장이며, 이후 경찰청 동기화도 선택을 유지합니다(선택한 사진이 사라지면 첫 활성 사진으로 돌아갑니다).
- ‘검토 완료’ 체크는 DB가 아닌 이 브라우저(localStorage)에만 저장합니다.
- 이 PC에서 `localhost`로 접속한 요청만 받습니다. 작업이 끝나면 백엔드를 종료합니다.

// TODO 배포 전: SecurityConfig의 동기화 POST·GET을 관리자 권한으로 제한하고, POST의 CSRF 예외를 제거하며 Swagger의 인증 요구도 복원해야 합니다.
