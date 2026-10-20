# 🔎 FindUs Backend

AI로 실종아동의 현재 예상 모습을 만들고, 실종 정보 검색·공유와 공식 제보 연결을 돕는 서비스의 백엔드입니다. AI 이미지는 참고용이며 실제 신원 일치를 보장하지 않습니다.

📚 기획·PRD·발표자료·ERD는 [FindUs Documents](https://github.com/EST-findus/documents)에서 관리합니다. 실제 DB 변경용 Flyway SQL은 이 저장소의 `src/main/resources/db/migration/`에 유지합니다.

**아래 순서대로 서버를 실행하고 응답을 확인할 수 있습니다.** 각자 컴퓨터의 PostgreSQL·Redis에 연결합니다.

## 1️⃣ 준비하기

- **Git**: 프로젝트를 내려받는 도구
- **JDK 17**: Java 서버를 실행하는 도구
- **Docker Desktop**: DB와 Redis를 실행하는 도구

Docker Desktop을 켜고, 터미널을 엽니다. Mac은 **터미널**, Windows는 **PowerShell**을 사용하세요. Gradle은 프로젝트에 포함되어 별도 설치하지 않습니다.

## 2️⃣ 프로젝트 내려받기

두 운영체제 모두 같은 명령을 사용합니다.

```sh
git clone https://github.com/EST-findus/findus-backend.git
cd findus-backend
```

이미 내려받았다면 다시 복제하지 말고 해당 폴더에서 작업하세요. 팀 개발 브랜치는 `dev`입니다. 최초 복제 후 `git switch dev`로 전환합니다.

## 3️⃣ 개인 설정 준비하기

**처음 실행할 때만** `.env.example`을 `.env`로 복사합니다. 기존 `.env`가 있으면 덮어쓰지 마세요. 기존 설정이 있다면 모니터링 비밀번호 두 항목도 확인한 뒤 4단계로 넘어갑니다.

**🍎 macOS**

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
[ -f .env ] || cp .env.example .env
openssl rand -base64 32
```

**🪟 Windows PowerShell** — JDK 경로는 본인이 설치한 위치로 바꿉니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& "$env:JAVA_HOME\bin\java.exe" -version
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

`.env`를 편집기로 열어 **아래 다섯 항목의 예시 값**을 바꾸고 저장합니다.

| 항목 | 넣을 값 |
| --- | --- |
| `POSTGRES_PASSWORD` | 본인 로컬 DB 비밀번호 · 영문·숫자 조합 권장 |
| `REDIS_PASSWORD` | 본인 로컬 Redis 비밀번호 · 영문·숫자 조합 권장 |
| `JWT_SECRET` | 바로 위 명령이 출력한 무작위 키 |
| `PROMETHEUS_PASSWORD` | Prometheus 수집 비밀번호 · 24자 이상 영문·숫자 |
| `GRAFANA_ADMIN_PASSWORD` | Grafana 최초 관리자 비밀번호 · 긴 무작위 영문·숫자 |

`java -version`에 **17**이 표시되어야 합니다. `.env`는 UTF-8로 저장하고 `KEY=value` 형식을 사용합니다. 실제 비밀번호·키는 Git에 올리지 않습니다. 기존 DB의 비밀번호는 `.env` 수정만으로 바뀌지 않습니다.

## 4️⃣ DB·Redis·모니터링 켜기

`build.gradle`이 있는 폴더에서 실행하세요.

```sh
docker compose up -d --wait
docker compose ps
```

PostgreSQL·Redis·Prometheus·Grafana와 수집용 exporter 두 개가 실행되면 컨테이너 준비 완료입니다. 메트릭 수집은 다음 단계에서 Spring 서버를 켜야 시작됩니다.

## 5️⃣ 서버 켜기

**🍎 macOS**

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

**🪟 Windows PowerShell**

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

처음에는 도구와 라이브러리를 내려받아 시간이 걸립니다. 아래 로그가 나오면 성공이며, **터미널은 켜 둡니다.**

```text
Tomcat started on port 18080
Started FindUsApplication
```

IntelliJ에서는 JDK·Gradle JVM을 17로 설정한 뒤 `FindUsApplication`을 실행해도 됩니다. 작업 폴더는 `findus-backend`로 지정합니다. 기본 `local` 프로필은 상위 `FindUs` 폴더에서 실행해도 백엔드 `.env`를 읽습니다. 서버는 한 번만 실행하세요.

## 6️⃣ 서버 응답 확인하기

브라우저에서 **[🩺 서버 확인](http://localhost:18080/health)**을 엽니다.

```text
http://localhost:18080/health
```

아래 항목이 모두 **`ok`**면 서버·DB·Redis와 로컬 모니터링 서비스 확인이 정상입니다. PostgreSQL은 `SELECT 1`, Redis는 `PING`으로 확인합니다. Prometheus는 준비 상태, Grafana는 자체 DB 연결 상태를 검사합니다.

```text
Spring Boot: ok
PostgreSQL: ok
Redis: ok
Prometheus: ok
Grafana: ok
PostgreSQL exporter: ok
Redis exporter: ok
```

로그인은 필요하지 않습니다. 확인 실패 항목은 `fail`로 표시됩니다. exporter는 외부 포트를 열지 않고 Prometheus의 최근 45초 이내 수집 결과로 확인하므로 상태 변화가 즉시 반영되지는 않습니다. Prometheus에 연결할 수 없으면 exporter도 확인할 수 없어 `fail`로 표시합니다. 모니터링 검사 항목은 `local` 프로필에서 활성화합니다. 모두 정상이면 HTTP **200**, 하나라도 실패하면 **503**입니다. `localhost`는 자신의 컴퓨터이며, 서버 포트를 바꿨다면 주소의 `18080`도 바꿉니다.

## 7️⃣ 회원가입 확인하기

Postman에서 아래 요청을 보냅니다. 테스트용 이메일은 매번 다르게 입력하세요.

- 메서드: **POST**
- 주소: `http://localhost:18080/api/members`
- Body: **raw → JSON**

```json
{
  "email": "test@example.com",
  "password": "Local-test123!",
  "name": "테스트회원",
  "nickname": "찾음이"
}
```

**201** 응답이면 회원이 로컬 DB에 저장됐습니다. 같은 이메일로 다시 가입하면 **409**가 반환됩니다. 로그인 요청 방법은 아래 React 연결 안내를 참고하세요.

## 📖 Swagger에서 API 테스트하기

서버를 켠 뒤 [Swagger UI 열기](http://localhost:18080/swagger-ui/index.html)를 클릭합니다. `local` 프로필에서만 제공됩니다. 별도 설치나 문서용 로그인은 필요하지 않습니다.

1. **회원 → 회원가입**을 펼쳐 `Try it out`을 누릅니다. 테스트 이메일·비밀번호·이름·닉네임을 입력하고 `Execute`를 누릅니다. **201**이면 DB에 저장됩니다. 같은 이메일은 **409**입니다.
2. **인증 → 보안 토큰 준비**에서 `Try it out → Execute`를 누릅니다. 브라우저에 CSRF 쿠키가 준비되며 이후 쓰기 요청에 자동으로 전달됩니다.
3. **인증 → 로그인**에 가입한 이메일·비밀번호를 입력합니다. **200** 응답의 `accessToken` 값을 복사합니다.
4. 화면 위 **Authorize**를 눌러 토큰 값만 붙여넣고 `Authorize → Close`를 누릅니다. `Bearer`는 직접 붙이지 않습니다.
5. **회원 → 내 정보 조회**를 실행해 로그인 정보를 확인합니다. **게시글 → 작성·목록·상세·수정·삭제**도 테스트할 수 있습니다. 작성 후 받은 `id`를 상세·수정·삭제에 넣습니다. 목록의 `page=0`은 첫 페이지입니다.
6. **토큰 재발급**을 실행하면 새 `accessToken`으로 Authorize를 갱신합니다. **로그아웃** 후에는 Authorize 창에서도 `Logout`을 눌러 토큰을 지웁니다.

**주의:** 실제 로컬 DB에 저장·수정·삭제되는 요청입니다. 테스트 데이터를 사용하세요. Refresh 쿠키는 브라우저가 관리하므로 직접 입력하지 않습니다. **401**은 로그인·토큰을, **403**은 보안 토큰 준비와 작성자 권한을 확인하세요. 페이지를 새로고침하면 Authorize 토큰은 다시 입력해야 합니다. UI와 API 모두 같은 `localhost` 주소를 사용합니다.

OpenAPI JSON 주소는 `http://localhost:18080/v3/api-docs`입니다. 메트릭 포트 `19081`과 구분하세요. 라이브러리를 추가했으므로 IntelliJ의 Gradle 새로고침 후 서버를 재시작합니다.

## 8️⃣ 마무리하기

서버 실행 터미널에서 **Ctrl+C**를 누른 뒤 DB·Redis를 종료합니다.

```sh
docker compose down
```

저장된 회원 데이터는 유지됩니다. **`down -v`는 데이터를 삭제하므로 일반 종료에 사용하지 마세요.**

## 🧯 실행이 안 될 때

| 상황 | 확인할 것 |
| --- | --- |
| Git 복제 권한 오류 | GitHub 로그인과 저장소 접근 권한 |
| Docker 연결 오류 | Docker Desktop 실행 여부 |
| `Permission denied` · Mac | `chmod +x gradlew` 후 재실행 |
| Java 버전 오류 | JDK 17과 `JAVA_HOME` 설정 |
| JWT 설정 오류 | `.env`의 `JWT_SECRET`에 생성한 키를 넣었는지 확인 |
| DB 비밀번호 오류 | 기존 DB 비밀번호와 `.env`·IDE 환경변수가 같은지 확인 |
| 주소가 열리지 않음 | 서버 시작 로그·포트 확인 |
| 회원가입이 실패함 | 응답의 JSON 오류 안내 확인 |

## 🧰 개발자용 참고

- 환경: Java 17 · Spring Boot 4.1.1 · Gradle Wrapper · PostgreSQL 17.11 · Redis 7.4.11
- 포트: 서버 `18080` · PostgreSQL `15432` · Redis `16379`
- 자동 테스트: Mac `./gradlew test` · Windows `.\gradlew.bat test` · Docker 필요
- 테스트 보고서: `build/reports/tests/test/index.html`
- DB 접속: `docker compose exec postgres psql -U findus -d findus -p 15432`
- DB 확인: `SELECT id, email, name, nickname, role, status FROM findus.members;`
- 회원 API: `POST /api/members` · `GET /api/members/me`
- 인증 API: `GET /api/auth/csrf` · `POST /api/auth/login` · `/api/auth/refresh` · `/api/auth/logout`
- Access 15분 · Refresh 7일 · 재발급 시 Refresh 교체 · 로그아웃 시 해당 로그인 세션 즉시 차단
- 연결 상태 확인: `GET /health` · 서비스별 일반 텍스트 (`text/plain`) · 정상 200 / 연결 실패 503
- React 기본 허용 주소: `http://localhost:5173` · 쿠키 요청은 `credentials: 'include'`
- HTTPS 배포 시 `AUTH_COOKIE_SECURE=true` · 다른 사이트에 배포하면 쿠키 정책 추가 검토
- `.env`·개인 IDE 설정·빌드 결과는 Git에서 제외합니다. 적용된 Flyway SQL은 수정하지 않습니다.
- Windows 실제 실행은 팀원 확인이 필요합니다.

## ⚛️ React에서 인증 연결하기

개발 주소는 `http://localhost:5173`, API 주소는 `http://localhost:18080`입니다. 양쪽 모두 `localhost`를 사용하세요. 프론트 주소가 바뀌면 `.env`의 `FRONTEND_ORIGIN`을 수정하고 서버를 재시작합니다.

1. 회원가입은 `POST /api/members`에 이메일·비밀번호·이름·닉네임을 JSON으로 보냅니다.
2. 로그인·재발급·로그아웃 전 `GET /api/auth/csrf`를 호출해 응답의 `token`, `headerName`을 받습니다.
3. 해당 POST 요청에 검증 헤더를 추가합니다. 모든 인증 요청은 `credentials: 'include'`를 사용합니다.
4. 응답의 Access Token은 메모리에 보관하고, 내 정보 조회에는 `Authorization: Bearer <토큰>`을 보냅니다. Refresh 쿠키는 브라우저가 관리합니다.

```javascript
const api = 'http://localhost:18080';

// React에서 로그인할 때 사용하는 예시입니다. 비밀번호와 토큰은 로그에 출력하지 않습니다.
async function login(email, password) {
  const csrfResponse = await fetch(`${api}/api/auth/csrf`, { credentials: 'include', cache: 'no-store' });
  if (!csrfResponse.ok) throw new Error('보안 토큰을 준비하지 못했습니다.');
  const csrf = await csrfResponse.json();
  const response = await fetch(`${api}/api/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify({ email, password })
  });
  if (!response.ok) throw new Error('로그인에 실패했습니다.');
  return response.json();
}
```

새로고침 후에는 CSRF를 준비하고 `/api/auth/refresh`로 Access Token을 다시 받습니다. 재발급은 동시에 여러 번 호출하지 마세요. 재발급도 401이면 로그인 화면으로 이동합니다. 헬스체크가 정상이어도 회원가입·로그인 동작은 별도로 테스트해야 합니다.

## 📝 게시글 API

목록·상세 조회는 공개이며, 작성·수정·삭제는 로그인이 필요합니다. **작성자만 수정·삭제**할 수 있습니다.

| 기능 | 메서드·주소 | 성공 응답 |
| --- | --- | --- |
| 작성 | `POST /api/posts` | 201 |
| 목록 | `GET /api/posts?page=0&size=10` | 200 |
| 상세 | `GET /api/posts/{id}` | 200 |
| 수정 | `PUT /api/posts/{id}` | 200 |
| 삭제 | `DELETE /api/posts/{id}` | 204 |

작성·수정 시 아래 JSON을 사용합니다. 제목은 최대 200자, 본문은 최대 10000자입니다. 작성자는 로그인 정보에서 자동으로 결정됩니다. 수정은 제목·본문 모두 보냅니다.

```json
{ "title": "첫 게시글", "content": "게시글 내용입니다." }
```

Postman에서는 로그인 후 받은 Access Token을 **Authorization → Bearer Token**에 넣습니다. 작성·수정·삭제 전에 `/api/auth/csrf`를 호출하고 응답의 `headerName`·`token`을 요청 헤더로 추가합니다. Postman의 쿠키 저장 기능도 켜 두세요. React도 동일한 헤더와 `credentials: 'include'`를 사용합니다.

- 첫 페이지는 **0**, 기본 10개·최대 100개입니다. `size`가 100보다 크면 100으로 제한합니다.
- 기본 정렬은 **작성 시간 내림차순**, 같은 시간이면 UUID 내림차순입니다. 새 글이 추가되면 페이지 위치는 달라질 수 있습니다.
- 정렬 예: `?sort=title,asc` · 허용 필드: `createdAt`, `updatedAt`, `title`, `id`
- 목록은 본문 없이 `content`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`, `hasPrevious`를 반환합니다.
- 삭제 시 `deleted_at`만 기록합니다. 삭제된 글은 목록·개수에서 제외되고 상세·수정·삭제 요청에 **404**를 반환합니다.
- 다른 작성자의 수정·삭제는 **403**, 잘못된 입력·정렬은 **400**입니다. 응답의 시간은 UTC 기준입니다.
- 본문은 일반 텍스트입니다. 프론트에서 HTML로 직접 삽입하지 않고 텍스트로 표시합니다.
- 좋아요·대댓글·첨부파일은 다음 작업에서 추가합니다.

DB 확인: `SELECT id, member_id, title, created_at, updated_at, deleted_at FROM findus.posts;`

## 💬 댓글 API

댓글 목록은 로그인 없이 조회할 수 있습니다. 작성·수정·삭제는 JWT와 CSRF가 필요하며 **댓글 작성자만 수정·삭제**할 수 있습니다. 게시글 작성자나 관리자에게 별도 삭제 권한은 부여하지 않습니다.

| 기능 | 메서드·주소 | 성공 응답 |
| --- | --- | --- |
| 작성 | `POST /api/posts/{postId}/comments` | 201 |
| 목록 | `GET /api/posts/{postId}/comments?page=0&size=10` | 200 |
| 수정 | `PUT /api/posts/{postId}/comments/{commentId}` | 200 |
| 삭제 | `DELETE /api/posts/{postId}/comments/{commentId}` | 204 |

Swagger에서는 다음 순서로 테스트합니다.

1. 기존 안내대로 **보안 토큰 준비 → 로그인 → Authorize**를 진행합니다.
2. 게시글을 작성하거나 목록에서 `id`를 확인합니다. 댓글 API의 `postId`에 넣습니다.
3. **댓글 → 댓글 작성**에서 아래 JSON을 보내고, 응답의 댓글 `id`를 확인합니다.
4. **댓글 목록**으로 저장 여부를 확인하고, 수정·삭제의 `commentId`에 댓글 ID를 넣습니다.
5. 삭제 후 목록·전체 개수에서 제외되는지 확인합니다. DB 행은 보존됩니다.

```json
{ "content": "도움이 되는 정보 감사합니다." }
```

- 댓글은 공백만 사용할 수 없으며 **최대 1000자**입니다. 줄바꿈은 유지합니다.
- 목록은 기본 **작성 시간·UUID 오름차순**, 첫 페이지 0·기본 10개·최대 100개입니다.
- 정렬 허용 필드: `createdAt`, `updatedAt`, `id` · 예: `?sort=createdAt,desc`.
- 응답에는 댓글 내용·게시글 ID·작성자 UUID와 닉네임·UTC 작성/수정 시간이 포함됩니다. 회원 개인정보나 엔티티 전체를 반환하지 않습니다.
- 댓글이 없으면 빈 페이지를 반환합니다. 없는 게시글·삭제된 게시글은 **404**입니다.
- 다른 게시글에 속한 댓글 ID나 삭제된 댓글의 수정·삭제도 **404**, 다른 작성자의 변경은 **403**입니다.
- 게시글이 삭제되면 그 댓글도 공개되지 않습니다. 댓글 행을 일괄 삭제하지는 않습니다.
- 본문은 일반 텍스트이며, 프론트에서도 HTML로 삽입하지 않고 텍스트로 표시합니다.
- 이번 작업에는 댓글 수·좋아요·대댓글을 포함하지 않습니다.

DB 확인: `SELECT id, post_id, member_id, content, created_at, updated_at, deleted_at FROM findus.comments;`

## 📊 Prometheus·Grafana 모니터링

**흐름:** Spring Boot가 메트릭 제공 → Prometheus가 15초마다 수집 → Grafana가 그래프로 표시합니다. 로컬 개발용 구성이며, `local` 외 프로필에서는 메트릭 경로를 활성화하지 않습니다.

1. 기존 `.env`에 `PROMETHEUS_PASSWORD`와 `GRAFANA_ADMIN_PASSWORD`를 추가합니다. `.env.example`의 설명을 참고하세요. 기존 DB 비밀번호·JWT 키는 바꾸지 않습니다.
2. `docker compose config --quiet`로 설정을 검사한 뒤 `docker compose up -d --wait`를 실행합니다.
3. IntelliJ 또는 위의 Gradle 명령으로 **Spring 서버도 실행**합니다. API는 18080, 메트릭은 별도 19081 포트입니다.
4. [Prometheus 수집 상태](http://localhost:19090/targets)에서 `findus-backend`가 **UP**인지 확인합니다. 서버가 꺼져 있으면 **DOWN**입니다.
5. [Grafana](http://localhost:13000)에 `admin`과 `.env`의 `GRAFANA_ADMIN_PASSWORD`로 로그인합니다.
6. **Dashboards → FindUs**에서 백엔드·JVM 상세·PostgreSQL·Redis·요청 및 DB 트랜잭션 대시보드를 선택합니다. 데이터 소스와 대시보드 5개는 자동 등록됩니다.
7. `http://localhost:18080/api/posts`를 여러 번 호출하고 약 30초 후 요청 수 그래프를 확인합니다. 응답 시간 p95는 요청이 적으면 값이 없거나 불안정할 수 있습니다.

| 항목 | 주소·설명 |
| --- | --- |
| 기존 헬스체크 | `http://localhost:18080/health` · DB·Redis 연결 확인 |
| Spring 메트릭 | `http://localhost:19081/actuator/prometheus` · 수집 계정 인증 필요 |
| Prometheus | `http://localhost:19090` · 메트릭 수집·검색 |
| Grafana | `http://localhost:13000` · 그래프·대시보드 |

대시보드는 수집 상태, Java 프로세스 CPU·힙 메모리, 요청 수·응답 시간 p95·5xx 오류 수, Hikari DB 연결 수를 보여줍니다. PostgreSQL·Redis exporter가 내부 네트워크에서 서버 통계를 추가 수집합니다. 총 6개 컨테이너가 실행됩니다. PostgreSQL의 커밋·롤백 수에는 exporter 등 전체 DB 활동이 포함되며, API 요청 수 또는 Spring 메서드별 트랜잭션 수와 같은 값이 아닙니다.

**접속 계정 구분**

| 주소 | 사용자 이름 | 비밀번호 |
| --- | --- | --- |
| `19081/actuator/prometheus` · Spring 메트릭 | `prometheus` | `.env`의 `PROMETHEUS_PASSWORD` |
| `19090` · Prometheus 화면 | 없음 | 현재 구성은 로그인 없이 접속 |
| `13000` · Grafana 화면 | `admin` | `.env`의 `GRAFANA_ADMIN_PASSWORD` |

Prometheus가 계정을 필수로 요구하는 것은 아닙니다. 여기서는 Spring 메트릭 제공 경로에 수집 전용 Basic 인증을 적용했습니다.

**메트릭 인증하기** — 일반 브라우저에서는 인증 창에 아이디 `prometheus`와 `.env`의 `PROMETHEUS_PASSWORD`를 입력합니다. 내장 브라우저가 인증 창을 지원하지 않으면 아래 명령 또는 Postman의 Authorization → Basic Auth를 사용합니다. 비밀번호를 URL에 붙이지 않습니다. JSON으로 회원 로그인 오류가 계속 보이면 IntelliJ에서 Gradle을 새로고침하고 Spring 서버를 재시작합니다.

**메트릭을 터미널에서 확인하기** — 비밀번호를 명령에 직접 적지 않으면 입력을 요청합니다. 아이디는 `prometheus`이고 비밀번호는 `.env`의 `PROMETHEUS_PASSWORD`입니다.

🍎 macOS:
```sh
curl -u prometheus http://localhost:19081/actuator/prometheus
```

🪟 Windows PowerShell:
```powershell
curl.exe -u prometheus http://localhost:19081/actuator/prometheus
```

**문제 확인:** `docker compose ps`, `docker compose logs --tail=50 prometheus grafana`를 사용합니다. 수집 오류가 401이면 비밀번호 일치 여부, 연결 거부라면 Spring 실행과 19081 포트를 확인하세요. 비밀번호 변경 후 Spring을 재시작하고 `docker compose up -d --force-recreate prometheus`로 수집 컨테이너를 다시 만듭니다.

컨테이너의 `host.docker.internal`은 Mac·Windows Docker Desktop에서 PC의 Spring 서버를 가리킵니다. Grafana는 내부 주소 `http://prometheus:19090`으로 접속합니다. Compose의 서비스 이름이 컨테이너 간 주소가 됩니다.

메트릭은 최대 7일·512MB를 기준으로 보관합니다. 실제 수집 데이터와 Grafana 계정은 Docker 볼륨에 저장되어 Git에 올라가지 않습니다. `docker compose down`은 데이터를 보존하지만 **`down -v`는 DB·Redis·모니터링 데이터를 모두 삭제**합니다. Grafana 계정이 만들어진 후에는 `.env` 변경만으로 기존 로그인 비밀번호가 바뀌지 않으므로 Grafana 화면에서 변경하세요. 공유 대시보드는 `monitoring/grafana/dashboards/`의 JSON 파일에서 수정합니다. Prometheus Targets에서 `findus-backend`, `findus-postgres`, `findus-redis`가 모두 UP인지 확인하세요. exporter의 UP은 수집 프로그램 상태이고, 실제 DB·Redis 연결은 `pg_up`, `redis_up` 값이 1인지 확인합니다.

**JVM 상세 화면:** [FindUs JVM 상세](http://localhost:13000/d/findus-jvm)에서 힙 사용률·사용/확보/최대 용량, 힙 외·버퍼 메모리, GC 정지 시간·횟수, 객체 할당·승격, GC 후 생존 메모리, 스레드 상태, 클래스 수, 실행 시간을 확인합니다. 추가 라이브러리 없이 Actuator가 수집하는 지표를 사용합니다. 패널 제목 옆 설명에서 용어를 확인할 수 있습니다.

메모리가 증가했다가 GC 후 감소하는 것은 정상일 수 있습니다. 비슷한 요청량에서 GC 후에도 남는 메모리가 지속적으로 늘고 응답이 느려지는지 함께 보세요. GC 횟수가 많다는 사실만으로 장애는 아니며 정지 시간과 응답 시간을 함께 확인합니다. 스레드의 대기 상태도 정상적인 유휴 상태일 수 있습니다. GC가 아직 발생하지 않았거나 필요한 수집 표본이 부족하면 일부 패널은 0 또는 값 없음으로 표시됩니다. 이 화면은 메모리 누수나 스레드 교착 상태를 확정 진단하는 도구는 아닙니다.

**Grafana 폴더가 비어 있을 때:** 브랜치 전환 등으로 마운트한 폴더가 바뀌면 컨테이너가 현재 파일을 읽지 못할 수 있습니다. `docker compose up -d --force-recreate --no-deps grafana`로 Grafana만 다시 만들고 화면을 새로고침하세요. 기존 데이터 볼륨은 유지됩니다. 대시보드 등록 상태는 `/health`의 Grafana 응답 여부와 별도로 확인합니다.

## 🧪 로컬 더미 데이터

로컬 프로필에서만 동작합니다. 수동 작성 데이터는 건드리지 않습니다.

| 종류 | 회원 | 게시글 | 댓글 | 동작 |
| --- | --- | --- | --- | --- |
| 시작 갱신 | 1 | 3 | 6 | 서버 시작 때 내용·수정 시간·삭제 상태를 초기화 |
| 고정 | 5 | 36 | 72 | 직접 요청 시 한 번 생성, 이후 변경한 내용도 유지 |

**1.** 서버를 시작하면 시작 갱신 데이터가 준비됩니다. UUID·최초 생성 시간은 유지하며, 직접 추가한 댓글도 보존합니다. 갱신 데이터 자체에 한 수정·소프트 삭제는 다음 시작 때 초기화됩니다.

**2.** Swagger에서 `GET /api/auth/csrf`를 실행합니다.

**3.** **개발용 더미 데이터 → POST /api/dev/dummy-data**를 실행합니다. 주소는 `http://localhost:18080/api/dev/dummy-data`이며 주소창으로 열면 생성되지 않습니다. Swagger가 CSRF 쿠키를 읽어 전달합니다.

**4.** `changed: true`면 최초 생성, `false`면 이미 생성된 상태입니다. 응답의 개수는 최초 구성 수로, 현재 남아 있는 데이터 수는 아닙니다. 일부를 삭제해도 재요청으로 복구하지 않습니다.

**5.** `GET /api/posts`로 게시글을 조회하고 `id`를 복사해 댓글 API에 사용합니다. 목록은 페이지 단위로 반환됩니다.

| 로그인 이메일 | 공통 비밀번호 기본값 |
| --- | --- |
| `dummy.startup1@example.test` | `Dummy-test123!` |
| `dummy.persistent1@example.test` ~ `dummy.persistent5@example.test` | `Dummy-test123!` |

모든 더미 회원은 USER입니다. 로그인 후 Authorize를 하면 본인이 작성한 게시글·댓글을 수정·삭제할 수 있습니다.

필요하면 `.env`에 `DUMMY_STARTUP_ENABLED=false`를 추가해 시작 갱신만 끄거나, `DUMMY_DATA_ENABLED=false`로 기능 전체를 끕니다. `DUMMY_PASSWORD`는 신규 생성·시작 갱신에만 반영되고 이미 만든 고정 회원의 비밀번호는 바뀌지 않습니다. 생성은 한 트랜잭션으로 처리해 중간 실패 시 모두 롤백하며, 동시 요청도 중복 생성하지 않습니다.

## 🔄 안전Dream 실종자 수집·동기화

📖 팀원용 최초 설정·테스트 순서·예상 시간은 Documents 저장소의 [실종아동 로컬 테스트 가이드](https://github.com/EST-findus/documents/blob/main/guides/safedream-local-test.md)를 참고하세요.

**DB 데이터·사진 확인 페이지:** 백엔드를 `local` 프로필로 실행한 뒤 [실종아동 DB 확인](http://localhost:18080/test/safe182)을 엽니다. 회원가입·로그인 없이 이용할 수 있습니다.

1. 처음 페이지를 열면 로컬 DB만 조회합니다. DB가 비어 있으면 0명으로 표시됩니다.
2. ‘경찰청 동기화 → DB 저장’을 누르거나 `curl -X POST http://localhost:18080/api/admin/missing-sync`를 실행합니다. `.env`의 경찰청 인증정보가 필요하며 완료까지 수 분 걸릴 수 있습니다.
3. 페이지 버튼으로 실행하면 저장 후 자동 재조회합니다. curl로 실행했다면 ‘DB 새로고침’을 누릅니다.
4. 한 페이지에 10명, 페이지 번호는 10개씩 표시합니다. 아동별 DB 사진 전체를 표시하며 없으면 ‘저장된 사진 없음’, 읽지 못하면 ‘사진 조회 실패’로 표시합니다.
5. 브라우저를 새로고침하거나 페이지를 이동해도 경찰청에 재요청하지 않습니다. 화면의 DB UUID와 SQL 조회 결과를 비교할 수 있습니다.

HTML에는 개인정보·사진·인증키가 없으며 같은 서버의 조회 API로 표시합니다. `file://` 직접 열기는 지원하지 않고 서버 주소를 안내합니다. `./gradlew safeDreamPreview`도 이제 주소만 안내하며 데이터를 수집하거나 HTML·JSON을 생성하지 않습니다. 확인 페이지는 `local` 프로필에서만 제공합니다. [상세 안내](src/main/resources/local-preview/README.md)를 참고하세요.

Spring Batch가 실종아동 원본 분류 `010`의 전체 공개 데이터를 100건씩 조회해 신규 정보를 추가하고 변경된 정보를 갱신합니다. 경찰청 식별자 `sourceId`는 중복되지 않고, 우리 DB의 `id`는 UUID입니다. 기존 회원·게시글·댓글과 별도 테이블을 사용합니다.

**1.** `.env`에 `SAFE182_ESNTL_ID`, `SAFE182_AUTH_KEY`를 설정하고 서버를 재시작합니다. 실제 인증값은 Git·스크린샷·로그에 남기지 마세요. URL 기본값은 `https://www.safe182.go.kr`입니다.

**2.** MVP 단계에서는 회원가입·로그인·Authorize·CSRF 준비 없이 동기화를 실행할 수 있습니다. **배포 전에는 `SecurityConfig`의 해당 POST·GET 규칙을 `hasRole("ADMIN")`으로 변경하고, 동기화 POST의 CSRF 예외를 제거하며 Swagger의 관리자 인증 요구도 복원해야 합니다.**

**3.** Swagger에서 바로 **실종자 배치 / POST /api/admin/missing-sync**를 실행합니다. 요청 본문·인증 헤더는 필요 없습니다. 이전에 Authorize에 입력한 토큰이 만료됐다면 Logout으로 지운 뒤 실행합니다. 요청 완료 후 `executionId`, `status`, `counts`를 확인합니다. 성공은 `COMPLETED`, 실패는 `FAILED`입니다. 시작 자체가 실패하면 503을 반환합니다. 터미널에서는 `curl -X POST http://localhost:18080/api/admin/missing-sync`로 실행할 수 있습니다.

**4.** `GET /api/missing-persons?page=0&size=10`으로 목록을, 응답의 `id`를 사용해 `GET /api/missing-persons/{id}`로 상세를 조회합니다. 조회에는 로그인이 필요 없습니다. 응답의 `category`는 원본 코드(010: 아동, 060/061/062: 지적장애인, 070: 치매 등)를 유지합니다. 현재 API 공개 범위가 모든 성인 실종자를 포함한다고 가정하지 않습니다.

**5.** `GET /api/admin/missing-sync`에서 최근 실행 상태·시간을 확인합니다. 처리 건수는 수동 실행 응답과 Spring Batch 실행 컨텍스트에 보관합니다.

| 설정 | 동작 |
| --- | --- |
| `MISSING_SCHEDULE_ENABLED=false` | 기본 로컬 설정. 수동 실행만 사용 |
| `MISSING_SCHEDULE_ENABLED=true` | 한국 시간 04:00·16:00 예약 실행 |
| `MISSING_SYNC_CRON` | 필요한 경우 Spring 형식 예약식 변경 |

배포 시 서버 환경변수에 `SPRING_PROFILES_ACTIVE=prod`, 인증정보와 `MISSING_SCHEDULE_ENABLED=true`를 설정합니다. 예약 시각에 서버가 실행 중이어야 하며, 꺼진 동안 놓친 예약은 자동 소급 실행되지 않습니다. 수동 실행으로 보완합니다. 서버 시작 때 자동 수집하지 않습니다.

전체 페이지의 건수·식별자를 검증한 뒤 한 트랜잭션으로 반영합니다. 페이지 누락·인증 오류·수집 중 전체 건수 변경·DB 오류는 기존 데이터를 유지합니다. 일시적인 통신 오류·429·5xx는 페이지당 최대 3회 요청하며 1초·2초 간격으로 재시도합니다. 최대 50페이지(5,000건)를 초과하면 자동 반영하지 않으며 제한 설정은 `app.missing.max-pages`입니다. 전체 0건 응답도 데이터 보호를 위해 실패로 처리하므로 실제 공개 데이터가 0건이 된 경우 관리자 확인이 필요합니다.

최근 전체 수집에서 사라진 정보는 `source_available=false`로 표시하고 공개 조회에서 제외합니다. DB 행은 보존하며 **발견 완료로 판단하지 않습니다.** 이후 다시 공개되면 같은 UUID로 복구됩니다. 동시 실행은 PostgreSQL 잠금으로 중복 수집·반영을 막습니다.

**사진 저장:** Flyway V9가 `findus.missing_person_images`를 생성합니다. `missing_person_id` 외래키로 실종자와 1:N 연결하고 사진 순번·출처 URL·MIME 형식·크기·SHA-256·사진 BYTEA를 저장합니다. 출처 사진 URL은 세션에 의존하므로 직접 표시하지 않습니다. 아동별 쿠키 세션에서 상세 페이지를 연 뒤 실제로 나열된 사진만 내려받아 저장합니다. 사진 파일은 최대 2MB이며 일시적인 사진 오류는 같은 세션에서 한 번 재시도합니다. 재시도도 실패하면 기존 사진을 유지합니다. 정상 전체 목록에서 사라진 사진은 소프트 삭제하고 다시 나타나면 같은 UUID로 복원합니다.

**수동 실행 주소:** `POST http://localhost:18080/api/admin/missing-sync` (MVP: 비회원 허용, JWT·CSRF·요청 본문 없음). 같은 작업을 새벽 04시·16시 예약에서도 사용합니다. 아동별 사진 수신으로 완료까지 수 분 걸릴 수 있으므로 클라이언트 요청 제한 시간을 충분히 지정합니다. 수동 실행 응답의 `imagesSaved`는 수신·반영한 사진 수, `photoFailures`는 사진을 전부 확인하지 못한 아동 수입니다. 부분 사진 실패만 있으면 실종자 데이터와 정상 사진은 반영하고 기존 사진은 보존합니다. 전체 데이터 API 실패나 DB 오류는 전체 변경을 롤백합니다.

**조회:** `GET /api/missing-persons`와 `GET /api/missing-persons/{id}` 응답의 `images`는 이미지 UUID·순서·백엔드 기준 상대 URL·형식·크기를 포함합니다. 기존 `photoUrl` 필드는 대표 사진 URL로 유지합니다. 프론트에서는 상대 URL 앞에 백엔드 주소 또는 프록시 경로를 붙입니다. `GET /api/missing-persons/{id}/images/{imageId}`는 DB 사진을 쿠키 없이 반환합니다. 다른 아동의 사진, 삭제 사진, 공개 중단·삭제된 아동 사진은 404이며 캐시 저장을 금지합니다. 관심아동 응답에도 같은 이미지 정보를 포함합니다. 이미지 목록 조회는 BYTEA를 제외한 메타데이터만 한 번에 조회합니다.

**요청 수:** 정상 실행은 `ceil(아동 수/100)`회 목록 API + 아동당 상세 1회 + 사진당 다운로드 1회입니다. 168명·337장이면 `2 + 168 + 337 = 507`회입니다. 인증키를 사용하는 공개 목록 API 요청은 이 중 2회이며 나머지는 일반 웹 상세·사진 요청입니다. 재시도·대표 사진 대체 요청은 추가됩니다. 실제 성공 실행 응답의 `counts.requests`에 `api`, `detail`, `image`, `total`을 기록합니다. 일반 웹 요청의 운영 제한이 공개 API와 같다고 가정하지 않습니다. 상세 사진 경로는 공식 API 계약 대상이 아니므로 사이트 변경 시 대응이 필요합니다.

Grafana의 **FindUs 실종자 배치**에서 마지막 성공 이후 경과 시간·실행 성공/실패·처리 건수·실행 시간을 확인합니다. 실행 카운터는 서버 재시작 시 초기화되지만 Batch 이력과 마지막 수집 시각은 DB에 유지됩니다.

자동 테스트는 가상 HTTP API와 임시 DB를 사용합니다. 실제 API 테스트는 `SAFE182_LIVE_TEST=true`와 두 인증 환경변수를 명시적으로 지정한 경우에만 실행합니다. 실제 데이터도 임시 DB에 저장하며 일반 테스트·빌드에서는 실행하지 않습니다.

자료 출처: 경찰청. [공식 데이터 API](https://www.safe182.go.kr/home/api/guide5.do). 비상업적 이용·출처 표기 등 [안전Dream 이용약관](https://www.safe182.go.kr/home/api/provision.do)을 확인하세요.

## ⭐ 마이페이지 관심아동

로그인한 회원이 **원본 분류 코드 010**인 공개 실종아동을 관심 목록에 저장합니다. 현재 나이가 성인이어도 원본 코드가 010이면 등록할 수 있습니다. 치매·지적장애인 등 다른 코드는 등록할 수 없습니다.

| 메서드 | 주소 | 기능 |
| --- | --- | --- |
| PUT | `/api/members/me/favorites/{missingPersonId}` | 관심 등록·취소한 관심 복원 |
| DELETE | `/api/members/me/favorites/{missingPersonId}` | 내 관심 취소 |
| GET | `/api/members/me/favorites?page=0&size=10` | 내 관심 목록 |

**1.** 서버를 재시작해 Flyway V8을 적용합니다. 실종자 데이터가 없다면 수동 배치로 먼저 수집합니다.

**2.** Swagger에서 `GET /api/auth/csrf` → 로그인 → Authorize 순서로 준비합니다. 모든 관심 API는 JWT가 필요하고 PUT·DELETE는 CSRF도 필요합니다.

**3.** `GET /api/missing-persons`에서 `category`가 `010`인 대상의 **`id`(UUID)**를 복사해 관심 등록 URL에 넣습니다. 경찰청의 `sourceId`나 관심 응답의 `id`가 아닙니다. 등록 요청 본문은 필요 없습니다.

**4.** `GET /api/members/me/favorites`에서 저장된 아동을 확인하고, 같은 실종자 UUID로 DELETE 후 목록에서 제외되는지 확인합니다. 다시 PUT하면 같은 관심 UUID와 최초 등록 시각을 유지해 복원합니다. 등록은 200, 취소는 204이며 반복 요청도 중복 생성하지 않습니다.

목록의 기본 크기는 10, 최대 100입니다. 기본 정렬은 최초 등록 시각·UUID 내림차순이며 `sort`에는 `createdAt`·`id`만 허용합니다. 재등록해도 최초 등록 시각과 정렬 위치는 유지합니다. 이름·사진 등은 `child` 안에 들어갑니다.

| availability | 의미 | child |
| --- | --- | --- |
| AVAILABLE | 공개 중인 원본 코드 010 아동 | 아동 정보 포함 |
| UNAVAILABLE | 공개 중단 또는 소프트 삭제 | null |
| NOT_CHILD | 공개 중이지만 코드가 010에서 변경됨 | null |

공개 중단·삭제·분류 변경 후에도 관심 관계와 목록 개수는 유지하지만 이름·사진·장소 등 개인정보는 반환하지 않습니다. 상태 표시만 보여주고 취소할 수 있습니다. 원본이 다시 010으로 공개되면 같은 연결로 정보가 표시됩니다. 공개 중단·삭제 대상의 등록·재등록은 404, 다른 분류는 400입니다.

관심 목록은 JWT의 회원 UUID로만 조회·변경합니다. 요청으로 다른 회원 ID를 지정할 수 없습니다. 같은 회원의 동시 요청은 잠금으로 순서대로 처리하고, 회원·실종자 조합의 유니크 제약으로 중복 행을 방지합니다. 배치는 원본 식별자에 해당하는 실종자 UUID를 유지하므로 갱신 후에도 관심 연결이 유지됩니다.
