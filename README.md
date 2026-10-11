# 🔎 FindUs Backend

AI로 실종아동의 현재 예상 모습을 만들고, 실종 정보 검색·공유와 공식 제보 연결을 돕는 서비스의 백엔드입니다. AI 이미지는 참고용이며 실제 신원 일치를 보장하지 않습니다.

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

아래처럼 세 줄이 모두 **`ok`**면 서버·DB·Redis 연결이 정상입니다. PostgreSQL은 `SELECT 1`, Redis는 `PING` 요청으로 실제 연결을 확인합니다.

```text
Spring Boot: ok
PostgreSQL: ok
Redis: ok
```

로그인은 필요하지 않습니다. 연결 실패 항목은 `fail`로 표시됩니다. 모두 정상이면 HTTP **200**, 하나라도 실패하면 **503**입니다. `localhost`는 자신의 컴퓨터이며, 서버 포트를 바꿨다면 주소의 `18080`도 바꿉니다.

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
- 연결 상태 확인: `GET /health` · 세 줄의 일반 텍스트 (`text/plain`) · 정상 200 / 연결 실패 503
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
- 댓글·좋아요·첨부파일은 다음 작업에서 추가합니다.

DB 확인: `SELECT id, member_id, title, created_at, updated_at, deleted_at FROM findus.posts;`

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
