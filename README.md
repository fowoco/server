# FOWOCO Server

E-9 외국인근로자를 고용한 사업장의 HR·총무 업무를 안전한 Workflow로 운영하는 Spring Boot 백엔드입니다.

FOWOCO는 단순 번역 서비스가 아닙니다. 체류·계약·서류·신고·근로자 안내 업무를 업무카드로 만들고, 담당자가 필요한 정보·승인·증빙·다음 행동을 놓치지 않도록 돕습니다.

> AI는 판단자가 아니라 보조자입니다. AI 결과는 인증·사업장 권한·상태 전이·HR 승인·감사 로그 안에서만 사용합니다.

## 현재 상태

아래 표는 현재 코드 기준입니다.

| 항목 | 현재 |
| --- | --- |
| 기술 | Java 17, Spring Boot 4.1.0, Gradle |
| 구현 API | Health, Auth 5개, Task Workflow 7개, Approval·Audit 8개. 전체 계약은 실행 중인 Swagger에서 확인 |
| 계획 API | Wiki API 카탈로그와 관련 Issue에서 설계·추적 |
| 로컬 DB | H2 + Flyway Auth·Company·Worker·Task·Approval·Audit·Outbox schema |
| 개발·배포 DB | PostgreSQL + Flyway |
| 보안 | JWT Access Token, `ADMIN`·`HR`·`VIEWER` 역할, `company_id` 기반 ActorContext |
| 개발 기반 | Swagger UI, 공통 오류, `request_id`, CI와 Transactional Outbox 구성 완료 |
| AI·Workflow | Knowledge Catalog projection, Task·Checklist·승인·감사와 AI Runtime 계약·방어 검증 구현. Remote 연동·AiRun은 후속 Issue |

계획 문서는 현재 동작하는 API가 아닙니다. 구현의 원본은 코드·테스트와 실행 시 생성되는 OpenAPI이고, 장기 아키텍처 결정은 [ADR](docs/adr/README.md), 계획 범위와 예시는 [API 카탈로그](https://github.com/fowoco/server/wiki/09-API-Specification)와 Issue에서 확인합니다.

데이터베이스 구조는 [DB 문서 사이트](https://fowoco.github.io/server/)에서 전체
ERD, 테이블·컬럼·제약조건과 Flyway 적용 이력을 확인할 수 있습니다. 이 사이트는
`main`의 Migration을 일회용 빈 PostgreSQL에 적용해 자동 생성하며 실제 데이터는
포함하지 않습니다. PR 미리보기와 로컬 생성 방법은
[데이터베이스 문서 사용법](docs/database-documentation.md)을 봅니다.

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

### 회원가입·로그인·재발급·로그아웃 흐름

회원가입 화면은 `POST /api/v1/auth/signup`으로 사업장과 최초 `ADMIN` 계정을 함께
생성합니다.

```json
{
  "company_name": "한빛정밀",
  "display_name": "김경민",
  "email": "name@company.com",
  "password": "8자 이상의 비밀번호"
}
```

- Client 화면의 `workplace`는 `company_name`, `name`은 `display_name`으로 변환합니다.
- `confirmPassword`는 Client에서 일치 여부만 확인하고 Server에 보내지 않습니다.
- Client가 `role`이나 `company_id`를 선택할 수 없으며 최초 계정은 항상 `ADMIN`입니다.
- Company와 UserAccount는 같은 transaction에서 생성되어 하나만 남을 수 없습니다.
- 가입 성공은 `201 Created`이며 Token을 발급하지 않습니다. 사용자는 기존 로그인 API로
  로그인합니다.
- 이메일 인증·담당자 초대·MFA·비밀번호 재설정은 후속 기능입니다.
- 외부 공개 환경에서는 회원가입 endpoint에 Gateway 또는 배포 경계 Rate Limit을
  추가해야 합니다.

1. Client가 `POST /api/v1/auth/login`에 `email`, `password`를 보냅니다.
2. 서버는 JSON 본문에 짧게 사용하는 `access_token`을 반환합니다.
3. Refresh Token은 JSON에 넣지 않고 `HttpOnly` 쿠키로만 전달합니다.
4. 보호 API는 `Authorization: Bearer <access_token>` 헤더로 호출합니다.
5. Access Token이 만료되면 요청 본문과 Bearer Token 없이 `POST /api/v1/auth/refresh`를 호출합니다. 서버는 기존 Refresh Token을 한 번 사용한 것으로 처리하고 새 Access Token과 새 쿠키로 교체합니다.
6. `POST /api/v1/auth/logout`은 Refresh Token 묶음을 폐기하고 브라우저 쿠키를 삭제합니다. 토큰이 없거나 이미 폐기되었어도 `204 No Content`로 처리합니다.
7. `GET /api/v1/auth/me`에서 Access Token의 `user_id`, `company_id`, `roles`를 확인할 수 있습니다.

브라우저 Client는 로그인·재발급·로그아웃 요청 모두 `fetch` 또는 HTTP Client에 `credentials: "include"`를 설정해야 합니다. 여러 요청이 동시에 `401`을 받더라도 재발급은 한 번에 하나만 보내고 나머지 요청이 그 결과를 함께 기다리는 **single-flight** 방식으로 구현합니다. 같은 Refresh Token을 동시에 두 번 사용하면 탈취·재사용으로 판단되어 해당 토큰 묶음이 폐기될 수 있습니다.

로그아웃은 새 Access Token 발급 수단을 폐기하지만 이미 발급된 stateless JWT를 즉시 삭제하지는 못합니다. 현재 기본 설정에서는 기존 Access Token이 만료까지 최대 15분간 유효하므로 Client는 로그아웃 응답을 받는 즉시 메모리나 상태 저장소의 Access Token을 삭제해야 합니다.

### 업무카드·체크리스트 흐름

Task API는 `ADMIN`과 `HR`이 업무카드를 만들고 수정하게 하며, `VIEWER`는 같은 사업장의 업무만 조회할 수 있습니다.

```text
GET  /api/v1/workflow-catalogs
POST /api/v1/tasks
GET  /api/v1/tasks
GET  /api/v1/tasks/{taskId}
PATCH /api/v1/tasks/{taskId}
PATCH /api/v1/tasks/{taskId}/checklist-items/{itemId}
POST /api/v1/tasks/{taskId}/cancel
```

1. Server는 `fowoco/knowledge`가 소유한 Workflow release를 read-only projection으로 읽습니다.
2. Task를 만들 때 `workflow_id`와 `workflow_catalog_version`을 함께 고정합니다.
3. Workflow의 필수 slot이 부족하면 `NEEDS_INFO`, 충분하면 `DRAFT`로 생성합니다.
4. Checklist template은 Task별 항목으로 복사되며 Client가 임의 항목을 추가하거나 필수 여부를 바꾸지 못합니다.
5. 수정·체크·취소 요청은 응답에 있는 최신 `version`을 `expected_version`으로 보내야 합니다. 오래된 화면의 값이면 `409 CONCURRENT_MODIFICATION`입니다.
6. 승인된 날짜·금액·설명 같은 중요값을 바꾸면 기존 승인을 무효화합니다. 필수정보와 checklist가 충분하면 수정본 승인 snapshot을 새로 만들고 `READY_FOR_REVIEW`, 부족하면 `NEEDS_INFO`가 됩니다.
7. `status`와 `company_id`는 쓰기 요청으로 받지 않습니다. 상태는 명시적인 Server command가, 사업장은 JWT의 ActorContext가 결정합니다.

로컬·테스트에서는 저장소의 `catalog-projection.local.json`으로 개발할 수 있습니다. 이 파일은 Knowledge `0.2.0 DRAFT`의 개발용 projection이며 원본 Catalog가 아닙니다. 운영 `prod` Profile은 `WORKFLOW_CATALOG_LOCATION`에 배포된 `RELEASED` projection을 반드시 지정해야 하고 DRAFT bundle이면 서버 시작을 거부합니다.

### 승인·감사 흐름

승인 API는 `ADMIN` 또는 `HR` 역할만 변경할 수 있고, 조회용 업무 활동은 `VIEWER`도 볼 수 있습니다. 사업장 전체 감사 검색은 `ADMIN`만 가능합니다.

```text
POST /api/v1/tasks/{taskId}/approval-requests
→ POST /api/v1/tasks/{taskId}/approve 또는 /reject
→ POST /api/v1/tasks/{taskId}/external-submissions (필요한 업무)
→ POST /api/v1/tasks/{taskId}/evidence
→ POST /api/v1/tasks/{taskId}/complete
```

- 승인 요청은 AI 원본, HR 최종본, 변경 필드, source version을 snapshot으로 보존합니다.
- 주민·외국인등록번호, 여권번호, 전화번호, 계좌번호, 토큰, 비밀번호, 전체 Prompt가 snapshot에 섞이면 요청 전체를 취소합니다.
- `task.version`은 동시에 수정한 요청의 충돌을 찾고, `content_revision + critical_fingerprint`는 현재 내용에 기존 승인을 재사용할 수 있는지 판단합니다.
- 상태 변경, 승인 기록, 감사 이벤트는 같은 DB transaction에 기록되므로 중간 하나가 실패하면 함께 되돌아갑니다.
- `GET /api/v1/tasks/{taskId}/activities`는 화면용 안전 타임라인이고, `GET /api/v1/audit-events`는 ADMIN용 필터·cursor 조회입니다. 내부 snapshot 원문은 두 API에 노출하지 않습니다.

### 이벤트 유실 방지와 재처리

Task 생성·취소처럼 후속 처리가 필요한 변경은 업무 데이터와 `event_publication`을
하나의 DB transaction에 저장합니다. 따라서 서버가 commit 직후 종료되어도
이벤트가 사라지지 않습니다.

```text
업무 transaction
→ 업무 데이터 + event_publication 함께 commit
→ Outbox worker가 lease 획득
→ handler 실행 + event_consumption 기록
→ COMPLETED
```

- 일시적 실패는 지수 backoff 후 `RETRY_WAIT`에서 다시 처리합니다.
- 처리 중 서버가 종료되면 lease 만료 후 다른 서버가 이어받습니다.
- `(event_id, handler_name)` 완료 기록으로 이미 성공한 handler를 다시 실행하지 않습니다.
- 재시도 한도 초과, 잘못된 payload 같은 영구 실패는 버리지 않고
  `REVIEW_REQUIRED`로 남깁니다.
- Event payload는 기능별 allow-list를 통과한 작은 업무값만 허용합니다. 이름·이메일,
  전화번호, 여권번호, 토큰, 비밀번호, 전체 Prompt는 저장할 수 없습니다.

현재 Task 모듈은 `TaskCreated`, `TaskCancelled`를 발행합니다. 새 handler를 추가하는
방법, 장애 확인 SQL과 설정값은
[Transactional Outbox 운영 가이드](docs/reliability/transactional-outbox.md)를
확인합니다.

### AI Runtime 계약 기반

Server는 AI Runtime에 보낼 수 있는 field를 typed DTO로 제한하고, 전송 전과 응답 후에
`ValidatingAiRuntimeClient`로 개인정보·request ID·version·worker·workflow·slot을
검증합니다.

- `AiRuntimeClient`는 Provider-neutral Port이며 OpenAI·Gemini SDK를 포함하지 않습니다.
- 테스트에서는 네트워크를 호출하지 않는 `FakeAiRuntimeClient`를 사용합니다.
- 실제 `RemoteAiRuntimeClient`와 `/internal/v1/analyses` HTTP 연결은 AI 저장소의 원본
  OpenAPI·JSON Schema가 release된 뒤 추가합니다.
- Remote Client는 자동 retry하지 않습니다. 후속 #24가 새 AiAttempt를 영속한 경우에만
  다시 호출할 수 있습니다.

요청·응답 예시와 차단 규칙은
[Server ↔ AI Runtime 계약 기반](docs/ai-runtime-contract.md)에서 확인합니다.

### PostgreSQL 개발 Profile

```bash
export DB_URL=jdbc:postgresql://localhost:5432/fowoco
export DB_RUNTIME_USERNAME='제한된 애플리케이션 계정'
export DB_RUNTIME_PASSWORD='로컬 Secret'
export DB_MIGRATION_USERNAME='Flyway 전용 계정'
export DB_MIGRATION_PASSWORD='로컬 Secret'
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

`.env.example`은 필요한 변수의 예시이며 Spring Boot가 자동으로 읽지는 않습니다. 위처럼 환경변수로 내보내거나 IDE 실행 설정에 등록하세요.

runtime 계정은 일반 업무 DML만 수행하고, Flyway 계정은 migration을 적용합니다.
환경별 role 생성과 Secret 주입은 배포 작업에서 수행합니다. 실제 비밀번호·API
Key·토큰은 Git, Issue, Discussion, 로그에 올리지 않습니다.

### 선택 사항: local(H2) 데모 로그인 계정 만들기

local(H2)에 데모용 사업장과 `ADMIN` 계정이 필요할 때만 Seed를 명시적으로 켭니다.
기본값은 꺼짐이며 비밀번호 기본값도 없습니다. PostgreSQL `dev`·`prod`에서는 runtime
role에 전체 tenant 생성 권한을 주지 않으므로 이 Seed를 실행하지 않고, #9의
provisioning 단계에서 migration/provisioning credential로 초기 계정을 준비합니다.

```bash
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='로컬 또는 배포 Secret의 12자 이상 값'
./gradlew bootRun
```

서버는 Flyway 적용 뒤 사업장과 계정을 한 번만 만들고, 비밀번호 원문이 아니라 BCrypt hash만 저장합니다. 같은 설정으로 다시 실행해도 중복 생성하지 않습니다. 같은 이메일이 다른 사업장·사용자·역할로 이미 존재하면 덮어쓰지 않고 시작을 중단합니다.

이 값은 개인 `.env`에만 보관하고 `.env.example`, GitHub, 로그에 실제 비밀번호를
넣지 않습니다. ID·이메일·표시 이름·사업장 이름을 바꿔야 하면
`DEMO_SEED_COMPANY_ID`, `DEMO_SEED_ADMIN_USER_ID`, `DEMO_SEED_ADMIN_EMAIL`,
`DEMO_SEED_ADMIN_DISPLAY_NAME`, `DEMO_SEED_COMPANY_NAME`을 함께 설정할 수 있습니다.
최초 계정을 확인한 뒤에는
`DEMO_SEED_ENABLED=false`로 되돌려 의도하지 않은 Seed 실행을 막습니다.

## 개발 기반은 어떻게 동작하나요?

| 구성 | 초보자를 위한 설명 | 구현 위치 |
| --- | --- | --- |
| Profile | `local`은 H2, `dev`·`prod`는 PostgreSQL을 사용합니다. | `application.yaml` |
| Flyway | H2는 공통 migration만, PostgreSQL은 공통 및 PostgreSQL 전용 migration을 순서대로 실행합니다. | `db/migration`, `db/migration-postgresql` |
| Workflow Catalog | Knowledge release의 Server용 read-only projection을 시작 시 검증합니다. | `workflow/` |
| AI Runtime 계약 | 외부 AI 요청·응답을 allow-list와 version으로 다시 검증합니다. | `aiintegration/` |
| Transactional Outbox | 업무 변경과 후속 이벤트를 함께 저장하고 lease·재시도·멱등 기록으로 복구합니다. | `reliability/` |
| Security | JWT에서 ActorContext와 역할을 만들고 VIEWER의 쓰기 요청을 기본 차단합니다. | `SecurityConfig` |
| Swagger | Controller의 API 설명을 브라우저 문서로 보여줍니다. | `OpenApiConfig` |
| 공통 오류 | 모든 실패를 같은 JSON 구조로 반환합니다. | `common/error` |
| `request_id` | 한 요청의 응답과 서버 로그를 같은 ID로 찾게 해 줍니다. | `RequestIdFilter` |
| CORS | React 개발 서버 주소만 브라우저 교차 출처 요청을 허용합니다. | `CorsConfig` |
| Clock·UUID | 테스트에서 시간과 ID를 고정할 수 있게 공통 Bean으로 제공합니다. | `CommonBeanConfig` |
| CI | PR과 main 변경마다 Java 17, H2, PostgreSQL에서 테스트와 빌드를 확인합니다. | `.github/workflows/ci.yml` |

보호 API의 일반 인증은 `Authorization` 헤더 기반 JWT를 사용하고, 재발급·로그아웃만 HttpOnly Refresh Token 쿠키를 사용합니다. 현재 Spring Security의 CSRF token 검증은 비활성화되어 있으므로 MVP 쿠키는 same-site 배포에서 `SameSite=Strict` 또는 `Lax`만 허용합니다. `SameSite=None`은 CSRF token 또는 신뢰할 수 있는 Origin 검증을 구현하기 전에는 사용하지 않습니다. CORS 허용만으로 CSRF가 방지되는 것은 아닙니다.

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
    │       ├── workflow/
    │       │   └── catalog-projection.local.json # 개발용 Knowledge projection
    │       └── db/
    │           ├── migration/
    │           │   ├── V1__baseline.sql
    │           │   ├── V2__create_auth_company.sql       # Auth·Company·Refresh Token
    │           │   ├── V3__create_worker_document.sql    # Worker·Document metadata
    │           │   ├── V4__create_task_workflow_core.sql # Task·Checklist·전이 이력
    │           │   ├── V5__create_approval_audit.sql     # 승인·제출·증빙·감사
    │           │   ├── V6__add_user_display_name.sql     # 회원가입 담당자 표시 이름
    │           │   └── V7__create_event_outbox.sql       # 내구성 이벤트·handler 완료 기록
    │           └── migration-postgresql/                 # RLS 등 PostgreSQL 전용 migration
    └── test/
        └── java/com/fowoco/server/
            ├── architecture/
            ├── auth/
            ├── worker/
            ├── task/
            ├── aiintegration/
            ├── airun/
            └── reliability/
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
- `workflow`은 Knowledge가 배포한 projection을 읽을 뿐 원본 Workflow 정의를 수정하지 않습니다.
- `worker`의 `WorkerTaskContextReader`는 #6이 Worker API·도메인을 대신 구현하지 않고 Task 판단에 필요한 최소 상태·날짜만 읽는 내부 경계입니다.
- 최상위 `package-info.java`는 기능 경계와 책임을 Git에 남기기 위한 뼈대입니다. 빈 하위 패키지는 미리 만들지 않고 실제 코드가 추가될 때 생성합니다.
- Flyway migration은 적용 후 수정할 수 없습니다. 후행 migration은 의존하는
  선행 schema가 `main`에 병합된 뒤 다음 사용 가능한 번호로 만들며, 번호 예약용 빈
  파일을 추가하지 않습니다.
- 테스트 패키지는 구현 패키지를 따라가고, `architecture`에는 향후 ArchUnit 또는 Spring Modulith 경계 검증을 둡니다.

## 어디서 무엇을 찾나요?

| 목적 | 위치 |
| --- | --- |
| 전체 백엔드 목표·작업 순서 | [MVP Epic #2](https://github.com/fowoco/server/issues/2) |
| 저장소·모듈·API·상태 결정 원본 | [Architecture Decision Records](docs/adr/README.md) |
| Server와 AI Runtime의 구현 계약 | [AI Runtime 계약 기반](docs/ai-runtime-contract.md) |
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
| PostgreSQL RLS 적용·복구 순서 | [RLS 단계적 도입 가이드](docs/database/postgresql-rls-rollout.md) |
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
