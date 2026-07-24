# FOWOCO Server 개발 가이드

README의 5분 실행 이후 인증, Workflow와 환경 설정을 이해하기 위한 문서입니다.
현재 동작하는 요청·응답의 원본은
[공유 Swagger](https://fowoco.github.io/server/api/)입니다.

## Profile과 데이터베이스

| Profile | 데이터베이스 | 사용 목적 |
| --- | --- | --- |
| `local` | 메모리 H2 | 처음 실행, 빠른 기능 개발 |
| `test` | 격리된 H2 | 자동화 테스트와 OpenAPI 문서 생성 |
| `dev` | PostgreSQL | 실제 DB 제약·동시성·RLS 개발 |
| `prod` | PostgreSQL | 배포 환경 |

`local`은 기본 Profile입니다. 서버를 다시 실행하면 메모리 DB가 초기화되고
Flyway Migration이 처음부터 적용됩니다. H2 Console 보호를 위해 기본적으로
내 PC의 `127.0.0.1`에서만 접근할 수 있습니다.

## 회원가입과 인증

### 사업장과 최초 관리자 생성

`POST /api/v1/auth/signup`은 사업장과 최초 `ADMIN` 계정을 하나의
Transaction으로 생성합니다.

```json
{
  "company_name": "한빛정밀",
  "display_name": "김경민",
  "email": "name@company.com",
  "password": "8자 이상의 비밀번호"
}
```

- Client의 `workplace`는 `company_name`, `name`은 `display_name`으로 변환합니다.
- `confirmPassword`는 Client에서만 확인하고 Server에 보내지 않습니다.
- Client가 `role`이나 `company_id`를 선택할 수 없습니다.
- 가입 성공은 `201 Created`이며 자동 로그인하지 않습니다.
- 이메일 인증·초대·MFA·비밀번호 재설정은 후속 기능입니다.
- 공개 환경에서는 Gateway 또는 배포 경계 Rate Limit이 필요합니다.

### 로그인·재발급·로그아웃

1. `POST /api/v1/auth/login`에 `email`, `password`를 보냅니다.
2. Server는 짧게 사용하는 JWT `access_token`을 JSON으로 반환합니다.
3. Refresh Token은 JSON이 아니라 `HttpOnly` Cookie로만 전달합니다.
4. 보호 API는 `Authorization: Bearer <access_token>`으로 호출합니다.
5. 만료 시 Bearer Token 없이 `POST /api/v1/auth/refresh`를 호출합니다.
6. `POST /api/v1/auth/logout`은 Refresh Token 묶음과 Cookie를 폐기합니다.
7. `GET /api/v1/auth/me`에서 현재 `user_id`, `company_id`, `roles`를 확인합니다.

브라우저 Client는 로그인·재발급·로그아웃 요청에 `credentials: "include"`를
사용합니다. 여러 요청이 동시에 `401`을 받아도 재발급은 한 번만 보내고 결과를
함께 기다리는 single-flight 방식이 필요합니다.

로그아웃해도 이미 발급한 stateless Access Token은 즉시 삭제할 수 없습니다.
Client는 응답 직후 메모리의 Token을 삭제해야 하며, 기본 Token은 최대 15분
안에 만료됩니다.

## 업무카드·체크리스트

`ADMIN`과 `HR`은 업무를 변경할 수 있고 `VIEWER`는 같은 사업장의 업무를
조회할 수 있습니다.

```text
GET   /api/v1/workflow-catalogs
POST  /api/v1/tasks
GET   /api/v1/tasks
GET   /api/v1/tasks/{taskId}
PATCH /api/v1/tasks/{taskId}
PATCH /api/v1/tasks/{taskId}/checklist-items/{itemId}
POST  /api/v1/tasks/{taskId}/cancel
```

1. Server는 `knowledge`가 배포한 Workflow projection을 읽습니다.
2. Task 생성 시 `workflow_id`와 `workflow_catalog_version`을 고정합니다.
3. 필수 Slot이 부족하면 `NEEDS_INFO`, 충분하면 `DRAFT`로 생성합니다.
4. Checklist template은 Task별 항목으로 복사됩니다.
5. 변경 요청은 최근 응답의 `version`을 `expected_version`으로 보냅니다.
6. 오래된 값이면 `409 CONCURRENT_MODIFICATION`으로 거부합니다.
7. 승인된 중요값을 바꾸면 이전 승인을 무효화하고 다시 검토합니다.
8. `status`와 `company_id`는 Client 입력이 아니라 Server가 결정합니다.

local·test에서는 `catalog-projection.local.json`을 사용합니다. `prod`는
`WORKFLOW_CATALOG_LOCATION`에 배포된 `RELEASED` projection이 필요하며 DRAFT
bundle이면 시작하지 않습니다.

## 승인·감사

승인 변경은 `ADMIN`과 `HR`, 사업장 전체 감사 검색은 `ADMIN`만 수행합니다.

```text
POST /api/v1/tasks/{taskId}/approval-requests
→ POST /api/v1/tasks/{taskId}/approve 또는 /reject
→ POST /api/v1/tasks/{taskId}/external-submissions
→ POST /api/v1/tasks/{taskId}/evidence
→ POST /api/v1/tasks/{taskId}/complete
```

- 승인 요청은 AI 원본, HR 최종본, 변경 필드와 source version을 snapshot으로 고정합니다.
- 민감정보·Token·비밀번호·전체 Prompt가 섞이면 요청 전체를 거부합니다.
- 상태 변경, 승인 기록과 감사 이벤트는 같은 DB Transaction에 기록합니다.
- `/activities`는 화면용 안전 타임라인이고 `/audit-events`는 관리자용 검색입니다.
- 내부 snapshot 원문을 조회 API에 그대로 노출하지 않습니다.

## AI Runtime 계약

Server는 AI에 보낼 수 있는 필드를 typed DTO로 제한하고 요청 전·응답 후에
개인정보, `request_id`, version, worker, workflow와 slot을 검증합니다.

- `AiRuntimeClient`는 Provider-neutral Port입니다.
- 테스트는 네트워크를 호출하지 않는 Fake Adapter를 사용합니다.
- OpenAI·Gemini SDK, Prompt와 모델 라우팅은 `ai` 저장소가 담당합니다.
- Remote Client는 투명하게 여러 번 retry하지 않습니다.
- 영속 AiRun이 새 Attempt를 만든 경우에만 다시 호출할 수 있습니다.

상세 계약은 [AI Runtime 계약 문서](ai-runtime-contract.md)를 확인합니다.

## PostgreSQL `dev` Profile

```bash
export DB_URL=jdbc:postgresql://localhost:5432/fowoco
export DB_RUNTIME_USERNAME='제한된 애플리케이션 계정'
export DB_RUNTIME_PASSWORD='로컬 Secret'
export DB_MIGRATION_USERNAME='Flyway 전용 계정'
export DB_MIGRATION_PASSWORD='로컬 Secret'
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

`.env.example`은 필요한 변수 목록이며 Spring Boot가 자동으로 읽지 않습니다.
환경변수 또는 IDE 실행 설정에 등록합니다.

runtime 계정은 업무 DML, migration 계정은 Flyway 적용만 담당합니다. 실제
비밀번호·API Key·Token은 Git, Issue, Discussion과 로그에 올리지 않습니다.

## 선택 사항: local Demo Seed

local H2에서만 사용할 사업장과 `ADMIN` 계정이 필요할 때 명시적으로 켭니다.
기본값과 비밀번호 기본값은 없습니다.

```bash
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='로컬 Secret의 12자 이상 값'
./gradlew bootRun
```

같은 설정으로 재실행해도 중복 생성하지 않습니다. 같은 이메일이 다른 사업장이나
역할로 존재하면 덮어쓰지 않고 시작을 중단합니다. 최초 계정을 확인한 후에는
`DEMO_SEED_ENABLED=false`로 돌려놓습니다.

PostgreSQL `dev`·`prod`에서는 Demo Seed 대신 배포 Provisioning 단계에서 초기
계정을 준비합니다.

## 개발 기반

| 구성 | 역할 | 구현 위치 |
| --- | --- | --- |
| Flyway | H2 공통·PostgreSQL 전용 Migration 관리 | `db/migration*` |
| Workflow Catalog | Knowledge release의 read-only projection 검증 | `workflow/` |
| Security | JWT를 ActorContext와 역할로 변환 | `SecurityConfig` |
| Swagger | Controller에서 OpenAPI·HTML 생성 | `OpenApiConfig` |
| 공통 오류 | 실패를 같은 JSON 형태로 반환 | `common/error` |
| `request_id` | 응답과 로그를 같은 ID로 추적 | `RequestIdFilter` |
| CORS | 등록한 Client Origin만 허용 | `CorsConfig` |
| Clock·UUID | 테스트에서 시간과 ID를 고정 | `CommonBeanConfig` |
| CI | H2·PostgreSQL 테스트와 빌드 | `.github/workflows/ci.yml` |

### CORS와 Cookie

Client 주소가 기본값인 `http://localhost:3000`, `http://localhost:5173`과
다르면 `CORS_ALLOWED_ORIGINS`에 쉼표로 구분해 등록합니다. `prod`에서는 이 값이
없으면 시작하지 않습니다.

현재 일반 인증은 Bearer JWT이며 재발급·로그아웃만 Refresh Token Cookie를
사용합니다. MVP Cookie는 same-site 배포의 `SameSite=Strict` 또는 `Lax`만
허용합니다. `SameSite=None`은 CSRF Token 또는 신뢰 Origin 검증을 구현한 뒤
사용합니다.

### 공통 오류

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

Client가 안전한 형식의 `X-Request-Id`를 보내면 Server가 응답과 로그에서 같은
값을 사용합니다. 생략하거나 형식이 잘못되면 Server가 새 ID를 만듭니다.

## 관련 문서

- [API 문서 사용법](api-documentation.md)
- [Database 문서 사용법](database-documentation.md)
- [프로젝트 구조](project-structure.md)
- [ADR 목록](adr/README.md)
- [PostgreSQL RLS 적용 가이드](database/postgresql-rls-rollout.md)
