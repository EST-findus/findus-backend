# FindUs Backend

FindUs는 실종아동의 과거 사진과 나이 정보를 바탕으로 AI가 현재 예상 모습을 만들고, 시민이 실종 정보를 검색·공유하며 공식 제보 채널로 연결될 수 있도록 돕는 서비스입니다.

AI 예상 얼굴은 참고 이미지이며 실제 외모나 신원 일치를 보장하지 않습니다.

이 저장소는 FindUs의 **백엔드(서버)** 코드입니다. 개발을 처음 접하는 팀원은 아래의 프로젝트 소개와 현재 진행 상황부터 읽어주세요. 코드만 확인하려면 서버를 실행할 필요는 없습니다.

## 백엔드는 어떤 역할을 하나요?

웹사이트에서 사용자가 보는 화면을 **프론트엔드**, 화면에서 요청한 일을 처리하고 데이터를 관리하는 프로그램을 **백엔드**라고 합니다.

예를 들어 회원가입 기능은 다음 순서로 동작하도록 개발할 예정입니다.

```text
사용자가 화면에서 가입 정보를 입력
    → 프론트엔드가 백엔드에 회원가입 요청
    → 백엔드가 입력 정보를 확인하고 DB에 저장
    → 백엔드가 처리 결과를 프론트엔드에 전달
    → 화면에 가입 결과 표시
```

| 용어 | 이 프로젝트에서의 의미 |
| --- | --- |
| Java | 백엔드 코드를 작성하는 프로그래밍 언어 |
| JDK | Java 코드를 빌드하고 실행하는 데 필요한 개발 도구 |
| Spring Boot | Java 서버를 구성하고 실행하도록 돕는 프레임워크 |
| Gradle | 필요한 라이브러리를 준비하고 코드를 빌드·테스트하는 도구 |
| Gradle Wrapper | 팀원이 같은 Gradle 버전을 사용하도록 저장소에 포함한 실행 도구 |
| API | 프론트엔드가 백엔드에 기능을 요청하는 약속된 주소와 방식 |
| DB | 회원 정보 등 데이터를 저장하는 데이터베이스 |
| PostgreSQL | 앞으로 사용할 DB |
| Redis | 앞으로 로그인 토큰 등 만료 시간이 있는 정보를 관리할 저장소 |
| Docker Compose | PostgreSQL·Redis 등의 실행 환경을 한 번에 구성할 도구 |

## 지금 어디까지 구현되어 있나요?

현재는 **1단계: 프로젝트 및 개발 환경 구성**입니다.

| 항목 | 현재 상태 |
| --- | --- |
| Java·Spring Boot·Gradle 기본 설정 | 구성 완료 |
| 기본 서버 시작 | 확인 완료 |
| 기본 애플리케이션 테스트·빌드 | 확인 완료 |
| 회원가입·로그인 API | 후속 단계에서 구현 |
| PostgreSQL·Redis 연결 | 후속 단계에서 구성 |
| Docker Compose | 후속 단계에서 구성 |
| React 화면·AI 기능 연동 | 아직 연결하지 않음 |

**서버가 실행된다는 것은 요청을 받을 기본 프로그램이 켜졌다는 의미입니다.** 회원가입이나 AI 기능이 완성되었다는 의미는 아닙니다. 현재 서버 실행에는 DB·Redis·Docker가 필요하지 않습니다.

## 개발 환경

| 항목 | 프로젝트 설정 |
| --- | --- |
| Java | JDK 17 |
| Spring Boot | 4.1.1 |
| Gradle | 9.7.1, Groovy DSL |
| 백엔드 개발 IDE | IntelliJ IDEA |
| 다른 팀원의 코드 확인·실행 | VS Code |
| 기본 서버 주소 | `http://localhost:8080` |

프로젝트는 Spring Initializr로 생성했습니다. Gradle은 저장소에 포함된 Wrapper를 사용하므로 별도 설치가 필요 없습니다. 최초 실행에는 Gradle과 라이브러리를 내려받기 위한 인터넷 연결이 필요합니다.

## 처음 실행하는 팀원을 위한 안내

### 1. 준비하기

1. **JDK 17**을 설치합니다. JDK는 IntelliJ나 VS Code와 별도로 필요한 Java 개발 도구입니다.
2. 이 저장소를 내려받고 `findus-backend` 폴더를 엽니다.
3. IntelliJ 또는 VS Code의 터미널을 엽니다. 터미널은 명령어를 입력하는 창입니다.
4. 터미널 위치가 `build.gradle`, `gradlew`, `gradlew.bat` 파일이 있는 폴더인지 확인합니다.
5. 아래 명령으로 Java 설치를 확인합니다.

```sh
java -version
```

출력에 `17` 버전이 표시되어야 합니다. 명령을 찾지 못하거나 다른 버전이 표시되면 JDK 설치와 `JAVA_HOME` 설정을 확인하세요. `JAVA_HOME`은 사용할 JDK의 설치 폴더를 지정하는 환경변수입니다.

### 2. 빌드하고 실행하기

본인의 운영체제에 해당하는 명령만 실행하세요. 빌드는 코드를 실행 가능한 형태로 준비하는 과정입니다.

#### macOS 터미널

설치한 JDK 17을 선택합니다.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

빌드와 테스트를 실행합니다. 마지막에 `BUILD SUCCESSFUL`이 표시되면 성공입니다.

```sh
./gradlew clean build
```

서버를 실행합니다.

```sh
./gradlew bootRun
```

#### Windows PowerShell

아래 경로는 예시입니다. **실제로 설치한 JDK 17 폴더 경로**로 바꾸세요. `bin` 폴더가 아니라 그 상위 JDK 폴더를 지정합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
```

빌드와 테스트를 실행합니다.

```powershell
.\gradlew.bat clean build
```

서버를 실행합니다.

```powershell
.\gradlew.bat bootRun
```

위 환경변수 설정은 현재 터미널에 적용됩니다. 새 터미널을 열면 다시 설정하거나 운영체제의 환경변수 설정에 등록해야 합니다.

### 3. 정상 실행인지 확인하기

터미널이나 IntelliJ 실행 창에서 다음 두 문장을 찾으세요. 앞에 붙는 시간과 실행 시간은 컴퓨터마다 다릅니다.

```text
Tomcat started on port 8080 (http) with context path '/'
Started FindUsApplication in ... seconds
```

첫 문장은 서버가 8080 포트에서 요청을 받을 준비가 되었다는 의미이고, 두 번째 문장은 애플리케이션 시작이 완료되었다는 의미입니다. Tomcat은 Spring Boot에 포함된 웹 서버라 별도 설치할 필요가 없습니다.

2026년 10월 5일 개발자가 IntelliJ에서 위 두 로그를 확인하여 기본 서버 실행을 확인했습니다.

브라우저에서 `http://localhost:8080`을 열어볼 수 있습니다. 여기서 `localhost`는 **접속하는 사람 자신의 컴퓨터**를 뜻합니다. 다른 팀원이 같은 주소를 입력해도 내 컴퓨터의 서버에 연결되는 것은 아닙니다.

**현재 `/` 주소에는 화면이나 API가 없어 404 또는 오류 안내 페이지가 표시될 수 있습니다.** 정상 시작 로그가 보인다면 이 응답만으로 서버 실행 실패라고 판단하지 않아도 됩니다. 아직 로그인 화면은 제공하지 않습니다.

서버 실행 중에는 터미널이 다음 명령을 입력할 상태로 돌아오지 않아도 정상입니다. Gradle의 진행 표시가 계속 남아 있어도 위 시작 로그가 보이면 실행된 상태입니다.

### 4. 서버 종료하기

- 터미널 실행: `Ctrl+C`를 누릅니다.
- IntelliJ 실행: 실행 창의 정지 버튼을 누릅니다.

종료 후에는 해당 주소에서 서버에 접속할 수 없습니다.

## IDE에서 실행하는 방법

### IntelliJ IDEA

1. `findus-backend` 폴더를 열고 Gradle 프로젝트로 가져옵니다.
2. Project SDK와 Gradle JVM을 **JDK 17**로 설정합니다.
3. Gradle 배포는 프로젝트의 **Wrapper**를 사용합니다.
4. Gradle 동기화가 끝나면 `src/main/java/com/findus/backend/FindUsApplication.java`를 엽니다.
5. `main` 메서드 옆 실행 버튼으로 실행합니다. 직접 실행이 어려우면 통합 터미널에서 `./gradlew bootRun`을 실행합니다. Windows에서는 `.\gradlew.bat bootRun`을 사용합니다.

### VS Code

1. JDK 17과 Java 개발용 확장인 **Extension Pack for Java**를 설치합니다.
2. `findus-backend` 폴더를 엽니다.
3. 프로젝트 가져오기가 끝날 때까지 기다립니다.
4. 통합 터미널에서 운영체제에 맞는 위 빌드·실행 명령을 사용합니다.

코드 확인만 하는 팀원은 Java 확장이나 서버 실행 없이도 파일 내용을 읽을 수 있습니다.

## 자주 만나는 문제

| 증상 | 확인할 내용 |
| --- | --- |
| `java` 명령을 찾을 수 없음 | JDK 17 설치와 PATH 설정을 확인하고 터미널을 다시 엽니다. |
| `JAVA_HOME` 경로 오류 또는 Java 17을 찾지 못함 | 실제 JDK 17 설치 폴더를 지정했는지 확인합니다. |
| `gradlew` 파일을 찾을 수 없음 | 터미널이 `findus-backend` 폴더에 있는지 확인합니다. |
| macOS에서 `Permission denied` | `chmod +x gradlew`를 실행한 뒤 다시 실행합니다. |
| Gradle·라이브러리 다운로드 실패 | 인터넷 연결을 확인합니다. 최초 실행에는 다운로드 시간이 걸립니다. |
| `Port 8080 was already in use` | 이미 실행 중인 같은 서버를 종료하거나 아래처럼 다른 포트를 사용합니다. |
| 브라우저에서 404 표시 | 현재 루트 주소에는 기능이 없습니다. 정상 시작 로그를 확인합니다. |
| 브라우저에서 연결할 수 없음 | 서버가 실행 중인지, 로그의 포트와 접속 주소가 같은지 확인합니다. |

8080 포트가 이미 사용 중이면 임시로 8081 포트에서 실행할 수 있습니다.

macOS:

```sh
./gradlew bootRun --args='--server.port=8081'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args='--server.port=8081'
```

이 경우 브라우저 주소도 `http://localhost:8081`로 바꿉니다.

## 주요 파일은 어떤 역할을 하나요?

| 파일 / 폴더 | 역할 |
| --- | --- |
| `src/main/java/` | 실제 서버 동작을 작성하는 Java 코드 |
| `FindUsApplication.java` | 서버 실행을 시작하는 진입점 |
| `src/main/resources/application.properties` | 애플리케이션 공통 설정. 현재는 앱 이름만 지정 |
| `src/test/java/` | 코드가 정상 동작하는지 확인하는 테스트 |
| `build.gradle` | Java·Spring Boot 버전, 라이브러리, 빌드 설정 |
| `settings.gradle` | Gradle 프로젝트 이름 설정 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Mac·Windows에서 공통 Gradle 버전을 실행하기 위한 파일 |
| `.gitignore` | Git에 포함하지 않을 개인 설정·생성 파일 규칙 |
| `.gitattributes` | Mac·Windows의 줄바꿈 차이 등을 관리하는 규칙 |
| `build/` | 빌드 결과와 테스트 보고서. 자동 생성되며 Git에서는 제외 |

앞으로 회원 기능은 `domain/user`, 인증 기능은 `domain/auth`, 공통 설정·응답·예외는 `common` 패키지로 나누어 작성할 예정입니다. 이 패키지들은 해당 구현 단계에서 추가합니다.

## 테스트와 확인 범위

`clean build`는 컴파일, 기본 Spring 컨텍스트 테스트, 실행 가능한 JAR 생성을 수행합니다. 기본 컨텍스트 테스트는 Spring의 기본 구성이 정상적으로 준비되는지 확인하며, 아직 회원가입이나 로그인 동작을 검사하는 테스트는 아닙니다.

테스트 보고서는 빌드 후 `build/reports/tests/test/index.html`에서 확인할 수 있습니다.

macOS·Java 17에서 빌드·기본 테스트·서버 실행을 확인했습니다. Windows와 각 팀원의 IDE에서도 실행 확인이 필요합니다.

## Git으로 공유하는 파일과 제외하는 파일

팀원이 같은 환경을 준비할 수 있도록 소스 코드, Gradle 설정·Wrapper, 공통 애플리케이션 설정과 이 README를 공유합니다. 추후 `Dockerfile`, 공통 Compose 파일, 비밀값 없는 서비스 설정, DB 마이그레이션 SQL도 공유합니다.

다음 항목은 각 컴퓨터에서 만들어지거나 비밀값이 들어갈 수 있어 제외합니다.

- IntelliJ의 `.idea/`, VS Code의 `.vscode/` 등 개인 IDE 설정
- Gradle 캐시·빌드 결과, OS·편집기 임시 파일
- `.env`, 로컬·비밀 설정, 개인 키·키스토어
- Docker 로컬 데이터, 로그·백업

`.env.example` 같은 예시 파일은 공유할 수 있지만 실제 비밀번호나 키를 넣으면 안 됩니다. 현재 프로젝트는 `.env`를 자동으로 읽도록 구성되어 있지 않으며, 환경변수 연결 방식은 2단계에서 정합니다.

추후 Docker bind mount 데이터는 `docker-data/<서비스>/` 아래에 두어 PostgreSQL·Redis·모니터링 데이터가 Git에 포함되지 않도록 합니다. Docker 이미지와 named volume은 저장소 외부에 보관됩니다. Docker Compose 구성은 3단계에서 진행합니다.

`.gitignore`는 이미 Git이 추적 중인 파일에는 적용되지 않습니다. 커밋 전 `git status --short`로 공유할 파일을 확인하세요.

## 앞으로의 개발 순서

1. **프로젝트·개발 환경 구성 — 현재 단계**
2. Git·환경변수 설정 관리
3. Docker Compose 기반 PostgreSQL·Redis 구성
4. UUID 회원 모델·DB 설계 및 JPA·Flyway 적용
5. 회원가입 구현
6. Spring Security·JWT 로그인 및 인증 구현
7. 통합 검증·React 연동 준비

각 단계는 이슈 작성 → 작업 브랜치 → 구현·검증 → 검토 → 커밋·푸시 → PR·머지 순서로 진행합니다. GitHub 등록·커밋·푸시·머지는 담당자가 직접 진행합니다.

## 참고 자료

- [Spring Initializr](https://start.spring.io/)
- [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- [기존 회원·인증 템플릿](https://github.com/pleasebelieveme/security-jwt-template)

