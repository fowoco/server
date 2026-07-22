# FOWOCO Server

E-9 외국인근로자를 고용한 사업장의 HR·총무 업무를 안전한 Workflow로 운영하는 Spring Boot 백엔드입니다.

FOWOCO는 단순 번역 서비스가 아닙니다. 체류·계약·서류·신고·근로자 안내 업무를 업무카드로 만들고, 담당자가 필요한 정보·승인·증빙·다음 행동을 놓치지 않도록 돕습니다.

> AI는 판단자가 아니라 보조자입니다. AI 결과는 인증·사업장 권한·상태 전이·HR 승인·감사 로그 안에서만 사용합니다.

## 현재 상태

아래 표는 현재 코드 기준입니다.

| 항목 | 현재 |
| --- | --- |
| 기술 | Java 17, Spring Boot 4.1.0, Gradle |
| 구현 API | `GET /health`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` |
| 계획 API | Wiki API 카탈로그와 관련 Issue에서 설계·추적 |
| 로컬 DB | H2 + Flyway Auth·Company schema |
| 개발·배포 DB | PostgreSQL + Flyway |
| 보안 | JWT Access Token, `ADMIN`·`HR`·`VIEWER` 역할, `company_id` 기반 ActorContext |
| 개발 기반 | Swagger UI, 공통 오류, `request_id`, CI 구성 완료 |
| AI·Workflow | 후속 Issue에서 구현 예정 |

계획 문서는 현재 동작하는 API가 아닙니다. 구현의 원본은 코드·테스트와 실행 시 생성되는 OpenAPI이고, 장기 아키텍처 결정은 [ADR](docs/adr/README.md), 계획 범위와 예시는 [API 카탈로그](https://github.com/fowoco/server/wiki/09-API-Specification)와 Issue에서 확인합니다.

## 5분 실행

### 필요한 것

- JDK 17
- Git
- PostgreSQL은 `dev` Profile을 사용할 때만 필요

### 테스트와 실행

```bash
git clone https://github.com/fowoco/server.git
cd server
./gradlew clean test
./gradlew bootRun
```

새 터미널에서 상태를 확인합니다.

```bash
curl http://localhost:8080/health
```

정상 응답은 `OK`입니다.

API 문서는 아래 주소에서 확인합니다.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- H2 Console(local 전용): <http://localhost:8080/h2-console>

local은 기본 Profile이라 별도 데이터베이스가 필요하지 않습니다. 서버를 다시 실행하면 메모리 DB가 초기화되고 Flyway migration이 처음부터 적용됩니다. H2 Console 보호를 위해 local 서버는 기본적으로 내 PC(`127.0.0.1`)에서만 접근할 수 있습니다.

### 로그인과 JWT 사용 흐름

1. Client가 `POST /api/v1/auth/login`에 `email`, `password`를 보냅니다.
2. 서버는 JSON 본문에 짧게 사용하는 `access_token`을 반환합니다.
3. Refresh Token은 JSON에 넣지 않고 `HttpOnly` 쿠키로만 전달합니다.
4. 보호 API는 `Authorization: Bearer <access_token>` 헤더로 호출합니다.
5. `GET /api/v1/auth/me`에서 토큰의 `user_id`, `company_id`, `roles`를 확인할 수 있습니다.

브라우저에서 다른 Origin의 Client가 로그인 쿠키를 받으려면 `fetch` 또는 HTTP Client에 `credentials: "include"`를 설정해야 합니다. 현재 구현 범위는 로그인과 Access Token 검증이며 Refresh Token 회전·로그아웃 API는 후속 커밋에서 연결합니다.

### PostgreSQL 개발 Profile

```bash
export DB_URL=jdbc:postgresql://localhost:5432/fowoco
export DB_USERNAME=postgres
export DB_PASSWORD='로컬에서만 사용하는 값'
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

`.env.example`은 필요한 변수의 예시이며 Spring Boot가 자동으로 읽지는 않습니다. 위처럼 환경변수로 내보내거나 IDE 실행 설정에 등록하세요.

실제 비밀번호·API Key·토큰은 Git, Issue, Discussion, 로그에 올리지 않습니다.

## 개발 기반은 어떻게 동작하나요?

| 구성 | 초보자를 위한 설명 | 구현 위치 |
| --- | --- | --- |
| Profile | `local`은 H2, `dev`·`prod`는 PostgreSQL을 사용합니다. | `application.yaml` |
| Flyway | 서버 시작 시 적용하지 않은 DB 변경 파일을 순서대로 실행합니다. | `db/migration` |
| Security | JWT에서 ActorContext와 역할을 만들고 VIEWER의 쓰기 요청을 기본 차단합니다. | `SecurityConfig` |
| Swagger | Controller의 API 설명을 브라우저 문서로 보여줍니다. | `OpenApiConfig` |
| 공통 오류 | 모든 실패를 같은 JSON 구조로 반환합니다. | `common/error` |
| `request_id` | 한 요청의 응답과 서버 로그를 같은 ID로 찾게 해 줍니다. | `RequestIdFilter` |
| CORS | React 개발 서버 주소만 브라우저 교차 출처 요청을 허용합니다. | `CorsConfig` |
| Clock·UUID | 테스트에서 시간과 ID를 고정할 수 있게 공통 Bean으로 제공합니다. | `CommonBeanConfig` |
| CI | PR과 main 변경마다 Java 17, H2, PostgreSQL에서 테스트와 빌드를 확인합니다. | `.github/workflows/ci.yml` |

Security는 쿠키 세션이 아닌 `Authorization` 헤더 기반 JWT를 전제로 CSRF를 비활성화합니다. 나중에 쿠키 인증을 도입한다면 이 설정을 반드시 다시 검토합니다.

Client 주소가 기본값(`http://localhost:3000`, `http://localhost:5173`)과 다르면 `CORS_ALLOWED_ORIGINS`에 쉼표로 구분해 등록합니다. `prod` Profile에서는 이 환경변수가 없으면 서버가 시작되지 않으므로 실제 Client 주소만 반드시 지정합니다.

공통 오류 응답 예시입니다.

```json
{
  "timestamp": "2026-07-22T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "path": "/api/v1/workers",
  "request_id": "01-example-request-id",
  "field_errors": [
    {
      "field": "display_name",
      "message": "값을 입력해 주세요."
    }
  ]
}
```

클라이언트가 `X-Request-Id` 요청 헤더를 보내면 서버가 안전한 형식인지 확인해 그대로 사용합니다. 생략하거나 형식이 잘못되면 새 ID를 만들고 응답 헤더와 오류 본문에 돌려줍니다.

## 대표 사용자 흐름

```text
HR 로그인
→ 근로자·서류 등록
→ 자연어 분석
→ 업무카드 후보 검토·확정
→ HR 승인
→ 근로자 보안 링크
→ 응답·증빙
→ 완료·감사 로그
```

대표 입력:

> 응웬반A 체류연장 준비하고 여권 사본도 요청해줘

기대 결과는 체류연장과 여권 사본 요청 후보 2개입니다. HR이 선택한 후보만 실제 업무가 되고, 승인 전에는 근로자에게 전달되지 않습니다.

## 구조

```text
React Client
  → Spring Boot Server
    → PostgreSQL
    → FileStorage Port
    → AiRuntimeClient
      → AI Runtime
        → External LLM API / Cloud Model Endpoint

Versioned Knowledge Bundle
  → AI Runtime
  → Server의 read-only Workflow projection
```

- `server`는 AI Runtime의 Internal Analysis API만 호출하며 Provider SDK와 Prompt를 포함하지 않습니다.
- `ai`는 Prompt, Agent Pipeline, 모델 routing과 Provider 호출을 담당합니다.
- `knowledge`는 Context Pack과 Workflow Catalog를 immutable version으로 배포합니다.
- LM Studio는 AI Runtime의 개발·모델 후보 실험에만 사용합니다.
- 최종 데모에서는 배포된 AI Runtime이 외부 LLM API 또는 Cloud Endpoint를 호출합니다.
- 상세 책임과 금지 의존성은 [ADR-0001](docs/adr/0001-repository-and-module-boundaries.md)을 따릅니다.

### Server 프로젝트 구조

Server는 하나의 Spring Boot 애플리케이션과 PostgreSQL로 배포하는 **modular monolith**입니다. Gradle 프로젝트를 기능마다 분리하지 않고, `com.fowoco.server` 아래에서 기능별 패키지 경계를 먼저 지킵니다.

```text
server/
├── build.gradle
├── settings.gradle
├── .env.example
├── README.md
├── CONTRIBUTING.md
├── docs/
│   └── adr/
│       ├── README.md
│       ├── 0001-repository-and-module-boundaries.md
│       ├── 0002-api-security-and-error-contract.md
│       └── 0003-task-airun-event-and-retry-model.md
└── src/
    ├── main/
    │   ├── java/com/fowoco/server/
    │   │   ├── ServerApplication.java
    │   │   ├── common/          # 기술 공통 코드만
    │   │   │   ├── config/
    │   │   │   ├── error/
    │   │   │   ├── id/
    │   │   │   ├── security/
    │   │   │   └── web/
    │   │   ├── health/          # 서버 상태
    │   │   ├── auth/            # 로그인, JWT, Refresh Token
    │   │   ├── company/         # 사업장과 사용자 권한
    │   │   ├── worker/          # 근로자
    │   │   ├── document/        # 서류 metadata
    │   │   ├── file/            # Local/S3 호환 파일 저장
    │   │   ├── workflow/        # 배포된 Workflow 조회
    │   │   ├── task/            # 업무카드와 상태 전이
    │   │   ├── approval/        # 승인 요청과 승인 snapshot
    │   │   ├── audit/           # append-only 감사 로그
    │   │   ├── workerlink/      # 근로자 보안 링크
    │   │   ├── airun/           # AiRun, candidate, 재시도
    │   │   ├── aiintegration/   # AI Runtime HTTP 연결
    │   │   └── reliability/     # Outbox와 event 복구
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/
    │           ├── V1__baseline.sql
    │           ├── V2__create_auth_company.sql      # Auth·Company·Refresh Token schema
    │           └── V3__create_worker_document.sql   # #5 구현 시 추가 예정
    └── test/
        └── java/com/fowoco/server/
            ├── architecture/
            ├── auth/
            ├── worker/
            ├── task/
            └── airun/
```

기능 코드가 생기면 해당 기능 안에서 다음 방향으로 확장합니다.

```text
<feature>/
├── api/             # Controller와 HTTP request/response DTO
├── application/     # Use case, command, query, port, transaction orchestration
├── domain/          # Aggregate, value object, 상태 전이와 불변식
└── infrastructure/  # JPA, HTTP client, storage 등 port 구현
```

- `api`는 `application`만 호출하고 JPA Repository나 외부 Client를 직접 호출하지 않습니다.
- `domain`은 Spring MVC, JPA, Provider SDK에 의존하지 않습니다.
- 다른 기능의 `infrastructure`와 JPA Entity를 직접 import하지 않습니다.
- `task` 이외의 기능은 Task 상태를 직접 변경하지 않습니다.
- `aiintegration`은 AI Runtime 연결만 담당하며 Prompt와 Provider SDK는 `ai` 저장소에 둡니다.
- 최상위 `package-info.java`는 기능 경계와 책임을 Git에 남기기 위한 뼈대입니다. 빈 하위 패키지는 미리 만들지 않고 실제 코드가 추가될 때 생성합니다.
- Flyway migration은 적용 후 수정할 수 없으므로 `V2`와 `V3` 빈 파일을 미리 만들지 않습니다. 각각 #4와 #5의 실제 스키마와 함께 추가합니다.
- 테스트 패키지는 구현 패키지를 따라가고, `architecture`에는 향후 ArchUnit 또는 Spring Modulith 경계 검증을 둡니다.

## 어디서 무엇을 찾나요?

| 목적 | 위치 |
| --- | --- |
| 전체 백엔드 목표·작업 순서 | [MVP Epic #2](https://github.com/fowoco/server/issues/2) |
| 저장소·모듈·API·상태 결정 원본 | [Architecture Decision Records](docs/adr/README.md) |
| 계획 API와 사용자 흐름 | [Wiki API 카탈로그](https://github.com/fowoco/server/wiki/09-API-Specification) |
| 사람이 읽는 상세 DTO·화면 기획 | [Notion API 명세](https://app.notion.com/p/f250e15aa74e82b8872581be4d7c6c3c?v=f280e15aa74e82ce8d6e8848514d41c3&pvs=23) |
| 화면·사용 흐름 | [Figma](https://www.figma.com/design/eaOD8OXZOGq6vK4H9pGXNi/FOWOCO?node-id=143-2&t=YbytLHiwZ5m1IChO-1) |
| 질문·아이디어·설계 비교 | [Discussions](https://github.com/fowoco/server/discussions) |
| 구현이 확정된 작업 | [Issues](https://github.com/fowoco/server/issues) |
| P0 핵심 일정 | [M3 Milestone](https://github.com/fowoco/server/milestone/1) |
| P1 사용성 일정 | [M4 Milestone](https://github.com/fowoco/server/milestone/2) |
| 서버 Issue·PR 로드맵 | [Server Roadmap · 팀원 전용](https://github.com/orgs/fowoco/projects/3) |
| 팀 전체 진행 상태 | [Project · 팀원 전용](https://github.com/orgs/fowoco/projects/1) |
| 아키텍처·보안·배포 설명 | [Server Wiki](https://github.com/fowoco/server/wiki) |
| 저장소 경계 설명 mirror | [Wiki 저장소 경계와 계약](https://github.com/fowoco/server/wiki/Repository-Boundaries-and-Contracts) |

## 변하지 않는 보안 원칙

- 모든 사업장 데이터는 인증 Context의 `company_id`로 격리합니다.
- MVP는 `ActorContext`, `company_id` 범위 Repository, tenant-aware DB 제약을 함께 사용합니다. PostgreSQL RLS는 DB Role·transaction context·connection pool 격리까지 검증한 후 도입합니다.
- 근로자는 로그인하지 않고 만료되는 보안 링크만 사용합니다.
- 외국인등록번호·여권번호·전화번호·계좌번호를 AI에 보내지 않습니다.
- AI 결과와 요청 초안은 HR 승인 전 자동 발송하지 않습니다.
- 중요한 변경은 actor, 시각, `request_id`와 함께 감사 로그에 남깁니다.
- Worker Link 원본 토큰, JWT, API Key, 비밀번호를 저장소와 로그에 남기지 않습니다.

## 기여하기

처음 참여한다면 [CONTRIBUTING.md](CONTRIBUTING.md)를 읽어 주세요.

- 질문이나 합의 전 아이디어는 Discussion에 작성합니다.
- 구현 범위와 완료 조건이 정해졌으면 Issue Form을 사용합니다.
- PR에는 관련 Issue, 변경 이유, 테스트, 보안 영향, 롤백 방법을 적습니다.
- 코드가 병합돼도 migration·Swagger·테스트·문서·필요한 배포가 남아 있으면 완료가 아닙니다.

## MVP 범위 밖

- 외부기관 자동 제출
- AI의 법률·노무 최종 판단
- 자체 학습 모델의 필수 서비스 탑재
- OCR·대용량 파일 처리 전체 구현
- 실제 Blue/Green Agent 트래픽 전환
