# 🔎 FindUs Backend

AI로 실종아동의 현재 예상 모습을 만들고, 실종 정보 검색·공유와 공식 제보 연결을 돕는 서비스의 백엔드입니다. AI 이미지는 참고용이며 실제 신원 일치를 보장하지 않습니다.

**개발을 몰라도 아래 순서대로 서버를 실행하고, 테스트 화면의 버튼으로 확인할 수 있습니다.** 각자 컴퓨터의 PostgreSQL·Redis에 연결합니다.

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

이미 내려받았다면 다시 복제하지 말고 해당 폴더에서 작업하세요. 테스트 화면은 이 변경사항이 포함된 브랜치에서 제공됩니다. PR 머지 전이라면 `git switch feat/member-auth`로 전환합니다.

## 3️⃣ 개인 설정 준비하기

**처음 실행할 때만** `.env.example`을 `.env`로 복사합니다. 기존 `.env`가 있으면 덮어쓰지 마세요. 세 항목이 이미 설정되어 있다면 4단계로 넘어갑니다.

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

`.env`를 편집기로 열어 **아래 세 항목의 예시 값**을 바꾸고 저장합니다.

| 항목 | 넣을 값 |
| --- | --- |
| `POSTGRES_PASSWORD` | 본인 로컬 DB 비밀번호 · 영문·숫자 조합 권장 |
| `REDIS_PASSWORD` | 본인 로컬 Redis 비밀번호 · 영문·숫자 조합 권장 |
| `JWT_SECRET` | 바로 위 명령이 출력한 무작위 키 |

`java -version`에 **17**이 표시되어야 합니다. `.env`는 UTF-8로 저장하고 `KEY=value` 형식을 사용합니다. 실제 비밀번호·키는 Git에 올리지 않습니다. 기존 DB의 비밀번호는 `.env` 수정만으로 바뀌지 않습니다.

## 4️⃣ DB와 Redis 켜기

`build.gradle`이 있는 폴더에서 실행하세요.

```sh
docker compose up -d --wait
docker compose ps
```

PostgreSQL과 Redis가 모두 **healthy**면 준비 완료입니다.

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

## 6️⃣ 테스트 화면 열기

브라우저에서 **[🧪 테스트 보드 열기](http://localhost:18080/test/mainboard)**를 누르거나 다음 주소를 입력합니다.

```text
http://localhost:18080/test/mainboard
```

`localhost`는 **자신의 컴퓨터**입니다. 팀원도 본인 컴퓨터에서 위 설정을 완료해야 합니다. `.env`의 서버 포트를 바꿨다면 주소의 `18080`도 바꾸세요.

테스트 URL에는 고정된 표준이 없습니다. 이 프로젝트는 테스트 기능을 모아 둔 **`/test/mainboard`**를 사용합니다. 이 화면과 관련 파일은 `local` 프로필에서만 제공됩니다.

## 7️⃣ 버튼을 눌러 확인하기

화면에 테스트 계정이 자동으로 준비됩니다. **테스트용 정보**를 사용하고, 1번부터 차례대로 누르세요. 로그인해야 다음 버튼이 활성화됩니다.

| 순서 | 버튼 | 정상 결과 |
| --- | --- | --- |
| 1 | 🙋 회원가입 | 201 · 실제 로컬 DB에 회원 저장 |
| 2 | 🔑 로그인 | 200 · 로그인 완료 |
| 3 | 👤 내 정보 조회 | 200 · 저장된 이메일·이름·닉네임 확인 |
| 4 | 🔄 토큰 재발급 | 200 · 로그인 유지용 새 토큰 발급 |
| 5 | 🚪 로그아웃 | 204 · 로그인 종료 |
| 6 | 🛡️ 로그아웃 후 접근 확인 | 401 · 이전 토큰의 접근 차단 |

오른쪽 **📬 서버 응답**에서 JSON 결과와 최근 기록을 확인합니다. 비밀번호는 응답에 포함되지 않고, 인증 토큰은 화면이 자동으로 관리합니다. 204는 응답 본문이 없는 정상 결과입니다.

추가 버튼으로 **중복 가입(409)·짧은 비밀번호(400)·틀린 비밀번호(401)·보안 토큰 누락(403)**도 확인할 수 있습니다. 이 경우에는 요청이 거부되어야 ✅ 성공입니다. 중복·틀린 비밀번호 확인은 회원가입 후 사용하세요.

다른 회원으로 반복하려면 로그아웃 후 **🎲 새 테스트 계정**을 누릅니다. 새로고침하면 화면의 토큰·기록은 초기화되지만, 저장된 회원과 브라우저 쿠키는 유지됩니다.

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
| 주소가 열리지 않음 | 서버 시작 로그·포트·local 프로필·브랜치 확인 |
| 버튼 결과가 예상과 다름 | 화면의 JSON 오류 안내를 확인하고 순서대로 재시도 |

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
- React 기본 허용 주소: `http://localhost:5173` · 쿠키 요청은 `credentials: 'include'`
- HTTPS 배포 시 `AUTH_COOKIE_SECURE=true` · 다른 사이트에 배포하면 쿠키 정책 추가 검토
- `.env`·개인 IDE 설정·빌드 결과는 Git에서 제외합니다. 적용된 Flyway SQL은 수정하지 않습니다.
- Windows 실제 실행은 팀원 확인이 필요합니다.
