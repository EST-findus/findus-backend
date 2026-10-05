# FindUs Backend

FindUs는 AI로 실종아동의 현재 예상 모습을 만들고, 실종 정보 검색·공유와 공식 제보 연결을 돕는 서비스입니다. AI 예상 얼굴은 참고 이미지이며 실제 신원 일치를 보장하지 않습니다.

이 저장소는 **백엔드(서버)** 코드입니다. 백엔드는 화면에서 보낸 요청을 처리하고 데이터를 관리합니다. 코드만 읽는 팀원은 서버를 실행하지 않아도 됩니다.

## 현재 상태와 개발 환경

| 항목 | 내용 |
| --- | --- |
| Java / Spring Boot | JDK 17 / 4.1.1 |
| 빌드 도구 | Gradle 9.7.1 · Groovy · Wrapper 사용 |
| 개발 도구 | 백엔드: IntelliJ · 팀원: VS Code |
| 데이터 저장소 | PostgreSQL 17.11 · Redis 7.4.11 |
| 현재 단계 | 3단계 Docker·DB·Redis 연결 구성 |
| 다음 작업 | 4단계 UUID 회원 모델·DB 설계 |

현재 회원·인증 API는 없습니다. **백엔드는 IntelliJ/터미널에서, PostgreSQL·Redis는 Docker에서 실행**합니다. Flyway가 DB 구조를 변경하고 JPA는 일치 여부만 검증합니다.

## 실행 전 준비 — 공통

1. JDK 17과 Docker Desktop을 준비하고 Docker Desktop을 실행합니다.
2. 이 저장소의 `findus-backend` 폴더를 엽니다.
3. IDE의 터미널을 엽니다. `build.gradle` 파일이 있는 폴더에서 명령을 실행하세요.
4. 아래에서 **본인의 운영체제에 해당하는 구역만** 따라 하세요.

Gradle은 Wrapper가 준비하므로 별도 설치하지 않습니다. 최초 실행에는 인터넷 연결이 필요합니다.

## 🍎 macOS — 터미널

JDK 17을 선택하고 버전을 확인합니다. 출력에 `17`이 표시되어야 합니다.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
java -version
```

최초 1회 `.env`를 준비합니다. **이미 있다면 복사하지 마세요.** 비밀번호 예시 두 곳을 개인 로컬 비밀번호로 바꿉니다.

```sh
cp .env.example .env
```

DB·Redis를 시작하고 빌드·테스트 성공(`BUILD SUCCESSFUL`) 후 서버를 실행합니다.

```sh
docker compose up -d --wait
./gradlew clean build
./gradlew bootRun
```

- 실행 권한 오류: `chmod +x gradlew` 실행 후 재시도
- 18080 포트가 사용 중: `./gradlew bootRun --args='--server.port=18081'`
- 종료: `Ctrl+C`

## 🪟 Windows — PowerShell

아래 경로를 **본인이 설치한 JDK 17 폴더**로 바꾸세요. `bin` 폴더가 아닌 상위 JDK 폴더입니다. 버전 출력에 `17`이 표시되어야 합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& "$env:JAVA_HOME\bin\java.exe" -version
```

최초 1회 `.env`를 준비합니다. **이미 있다면 복사하지 마세요.** 비밀번호 예시 두 곳을 개인 로컬 비밀번호로 바꿉니다.

```powershell
Copy-Item .env.example .env
```

DB·Redis를 시작하고 빌드·테스트 성공(`BUILD SUCCESSFUL`) 후 서버를 실행합니다.

```powershell
docker compose up -d --wait
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

- Wrapper를 찾지 못함: `findus-backend` 폴더에서 실행 중인지 확인
- 18080 포트가 사용 중: `.\gradlew.bat bootRun --args='--server.port=18081'`
- 종료: `Ctrl+C`

> 두 운영체제 모두 위 `JAVA_HOME` 설정은 현재 터미널에 적용됩니다. 새 터미널에서는 다시 설정해야 합니다.

## 환경변수 — 공통

위 복사 명령은 최초 1회만 실행합니다. 기존 `.env`가 있으면 복사하지 마세요. `.env`의 `SERVER_PORT`를 수정하면 다음 실행부터 적용됩니다. 값은 따옴표 없이 `KEY=value`로 작성하고 줄 끝 주석·`export`·역슬래시는 사용하지 않습니다. 파일은 UTF-8로 저장하세요.

기본 `local` 프로필에서 `findus-backend/.env`를 자동으로 읽습니다. IntelliJ에서 상위 `FindUs` 폴더를 열어 실행해도 읽을 수 있습니다. 두 위치에 `.env`가 모두 있으면 현재 작업 폴더의 파일이 우선합니다. **명령행 옵션 → OS/IDE 환경변수 → `.env` → 기본값** 순서로 우선 적용됩니다. 서버 실행에는 DB 접속 정보와 비밀번호가 필요합니다.

`POSTGRES_HOST`·`REDIS_HOST`는 `localhost`, 포트는 기본 `15432`·`16379`입니다. 충돌 시 `.env`의 포트를 바꾸고 컨테이너를 다시 시작하세요. 실제 비밀번호는 `.env`에만 작성합니다. 다른 프로필은 OS/IDE의 `SPRING_PROFILES_ACTIVE`로 선택하며 `.env`에는 넣지 않습니다.

## Docker·연결 확인 — 공통

`docker compose ps`에서 두 서비스가 `healthy`인지 확인합니다. IntelliJ에서도 실행 전 Compose를 시작하세요. 테스트는 개발 DB 대신 Testcontainers가 만드는 임시 DB·Redis를 사용하며 Docker가 필요합니다.

| 명령 / 항목 | 의미 |
| --- | --- |
| `docker compose up -d --wait` | DB·Redis 실행 및 준비 완료 대기 |
| `docker compose down` | 컨테이너 종료 · 저장된 데이터는 유지 |
| `docker compose logs --tail=30` | 최근 로그 확인 · 공유 전 민감값 확인 |
| `./gradlew test` / `.\gradlew.bat test` | DB/JPA 읽기·쓰기, Redis·인증·Flyway 통합 테스트 |

내 컴퓨터와 컨테이너 내부의 포트를 동일하게 사용합니다.

| 서비스 | 내 컴퓨터에서 접속 | 컨테이너 내부 포트 |
| --- | --- | --- |
| PostgreSQL | `localhost:15432` | `15432` |
| Redis | `localhost:16379` | `16379` |
| Spring Boot | `http://localhost:18080` | 현재 IntelliJ/터미널에서 실행 |

Docker Desktop의 PostgreSQL **Exec** 탭에서는 `psql -U findus -d findus`로 DB에 들어갑니다. 터미널에서는 `docker compose exec postgres psql -U findus -d findus`를 실행하세요. 접속 후 `\l`은 DB 목록, `\dn`은 스키마 목록, `\dt findus.*`는 회원용 스키마의 테이블 목록, `\q`는 종료입니다. 현재 회원 테이블은 없습니다.

Redis는 터미널에서 아래 명령으로 접속한 뒤 `PING`을 입력하여 `PONG`을 확인합니다.

```sh
docker compose exec redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -p "$REDIS_PORT"'
```

데이터는 Docker의 named volume에 보관됩니다. **`down -v`는 데이터를 삭제하므로 일반 종료에 사용하지 마세요.** PostgreSQL 비밀번호는 최초 DB 생성 때 설정되어 기존 볼륨이 있으면 `.env` 변경만으로 바뀌지 않습니다. 인증 오류가 나면 IntelliJ 실행 설정의 `POSTGRES_PASSWORD`·`SPRING_DATASOURCE_PASSWORD`가 `.env`를 덮어쓰고 있는지도 확인하세요.

앱 시작 시 Flyway가 `V1__create_application_schema.sql`을 적용하여 `findus` 스키마를 만들고, 재실행 시 중복 적용하지 않습니다. 회원 테이블은 4단계에서 새 마이그레이션으로 추가합니다. 적용된 SQL 파일은 수정하지 않습니다.

## 정상 실행 확인 — 공통

다음 로그가 보이면 서버가 시작된 상태입니다.

```text
Tomcat started on port 18080 (http) with context path '/'
Started FindUsApplication in ... seconds
```

기본 접속 주소는 `http://localhost:18080`입니다. `localhost`는 자신의 컴퓨터를 뜻합니다. `.env`나 실행 옵션에서 포트를 바꿨다면 접속 주소도 시작 로그의 포트에 맞추세요.

**현재 루트(`/`)에 API나 화면이 없어 404가 표시될 수 있습니다.** 위 시작 로그로 실행 여부를 확인하세요. 서버가 켜진 동안 터미널이 계속 실행 상태인 것은 정상입니다.

## IDE 설정 — 공통

- **IntelliJ:** Gradle 프로젝트로 열고 Project SDK·Gradle JVM을 JDK 17, Gradle 배포를 Wrapper로 설정합니다. Run Configuration의 Working directory를 `findus-backend` 루트로 지정하고 `FindUsApplication.java`의 `main`을 실행합니다. `.env` 플러그인은 필요 없습니다. 임시 값은 Environment variables에 `SERVER_PORT=18081`처럼 넣을 수 있습니다. 종료는 정지 버튼입니다.
- **VS Code:** 실행할 팀원은 JDK 17과 `Extension Pack for Java`를 준비한 뒤 통합 터미널에서 위 명령을 사용하세요. 코드 확인만 한다면 확장 설치는 필수가 아닙니다.

## 주요 파일과 Git 규칙

| 경로 | 역할 |
| --- | --- |
| `src/main/java/` | 서버 코드. `FindUsApplication.java`가 실행 진입점 |
| `src/main/resources/` | Spring Boot 설정 |
| `src/test/java/` | 자동 테스트 |
| `build.gradle` / `settings.gradle` | 버전·라이브러리·프로젝트 설정 |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/` | 공통 Gradle 실행 도구 |
| `.gitignore` / `.gitattributes` | 개인 파일 제외·줄바꿈 관리 |
| `docker-compose.yml` | 로컬 DB·Redis 실행과 데이터 유지 |
| `src/main/resources/db/migration/` | Flyway DB 변경 이력 |
| `.coderabbit.yaml` | 한국어 코드 리뷰·요약 설정 |

소스·Wrapper·공통 설정·`application-local.yml`·`.env.example`은 공유합니다. 개인 IDE 설정·빌드 결과·`.env`·비밀값·Docker 데이터·로그는 제외합니다. 실제 비밀번호는 공유 파일에 넣지 않습니다. `.env`는 루트 외부 파일이라 JAR에 포함되지 않습니다.

커밋 전 `git status --short`를 확인하세요. 이미 추적 중인 파일은 `.gitignore`만으로 제외되지 않습니다.

## 개발 순서

1. 프로젝트·개발 환경 구성
2. Git·환경변수 설정
3. **Docker·PostgreSQL·Redis·Flyway 구성 및 연결 검증 — 현재 작업**
4. UUID 회원 모델·DB 설계
5. 회원가입 구현
6. Spring Security·JWT 로그인·인증 구현
7. 통합 검증·React 연동 준비

이슈 → 브랜치 → 구현·검증 → 검토 → 커밋·푸시 → PR·머지 순서로 진행합니다. GitHub 작업은 담당자가 직접 수행합니다.

Windows 실행은 팀원 확인이 필요합니다. 테스트 보고서는 빌드 후 `build/reports/tests/test/index.html`에서 볼 수 있습니다. CodeRabbit은 앱 설치·저장소 접근이 완료되어 있어야 동작합니다. dev·main PR을 한국어로 리뷰하고 Draft는 제외하며, 요약은 짧게 제공합니다.
