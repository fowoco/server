# ADR-0002: API·보안·오류 계약

- Status: Proposed
- Date: 2026-07-22
- Deciders: FOWOCO Server Team
- Related Issue: [#23](https://github.com/fowoco/server/issues/23)
- Supersedes: None

## Context

FOWOCO에는 로그인 사용자가 호출하는 API, 로그인 없는 Worker Link API, Server가 소비하는 AI Runtime API가 있습니다. 경로·인증·tenant·오류 규칙을 기능마다 다르게 만들면 Client가 예외 처리를 반복하고, 다른 Company 데이터나 민감정보가 노출될 수 있습니다.

또한 기존 기획에는 `/tasks/analyze`, `/task-analyses/{id}/confirm`, `/ai-runs`가 같은 의미로 혼재했습니다. 하나의 기능은 하나의 canonical resource만 가져야 합니다.

## Decision

### 1. API namespace와 원본

| 범위 | 경로 | 인증 | 계약 Owner |
| --- | --- | --- | --- |
| HR·Admin·Viewer API | `/api/v1/**` | JWT Access Token. Login·refresh는 각 bootstrap credential 정책 적용 | `server` |
| Worker Link API | `/public/worker-links/**` | 만료·범위 제한 token | `server` |
| 운영 상태 | `/health`, 필요한 Actuator probe | 배포 환경별 최소 공개 | `server` |
| AI Runtime API | `/internal/v1/**` | S2S service credential | `ai` |

`/internal/v1/**`는 Server가 외부에 제공하는 Client API가 아닙니다. Server의 `RemoteAiRuntimeClient`가 `ai` 저장소의 계약을 소비합니다.

Server External API의 실행 계약 원본은 코드에서 생성되어 배포된 Swagger/OpenAPI와 자동화 테스트입니다. Notion과 Wiki는 설명과 계획을 제공하지만 구현되지 않은 API를 현재 동작하는 것처럼 표시하지 않습니다.

URI는 복수 resource noun과 `kebab-case`를 사용합니다. resource 생성은 `201 Created + Location`, 비동기 접수·retry는 `202 Accepted + Location 또는 status_url`, 상태를 바꾸는 동기 Command는 새 version을 포함한 `200 OK`를 기본으로 합니다. endpoint별 예외는 OpenAPI에 명시합니다.

### 2. 비동기 분석의 canonical resource

자연어 분석은 Task의 부가 동작이 아니라 추적 가능한 `AiRun` resource로 모델링합니다.

```http
POST /api/v1/ai-runs
GET  /api/v1/ai-runs/{aiRunId}
POST /api/v1/ai-runs/{aiRunId}/retry
POST /api/v1/ai-runs/{aiRunId}/candidate-decisions
```

- 생성은 `202 Accepted`, `ai_run_id`, `status_url`을 반환합니다.
- `/tasks/analyze`, `/task-analyses/{id}/confirm`, `/ai-runs/{id}/confirm`은 만들지 않습니다.
- candidate의 `ACCEPT`/`DISCARD` 결정과 Task의 HR `approve` Command를 분리합니다.
- Task 상태는 `PATCH /tasks/{id}`로 임의 대입하지 않고 명시적인 Command endpoint에서만 전이합니다.

### 3. JSON과 시간 형식

| 위치 | 규칙 |
| --- | --- |
| Java 식별자 | `camelCase` |
| Server External API JSON | `snake_case` |
| PostgreSQL | `snake_case` |
| AI Internal API | `ai`의 versioned contract 형식을 그대로 사용하고 경계 DTO에서 변환 |
| 시각 | UTC ISO-8601 `Instant`, 예: `2026-07-22T01:23:45Z` |
| 날짜 전용 값 | ISO-8601 `LocalDate`, 예: `2026-07-22` |
| 금액 | floating point를 사용하지 않고 통화 코드와 정수 또는 decimal string을 계약에 명시 |

API field 이름을 Java·DB·AI 계약에 억지로 동일하게 만들지 않습니다. Adapter가 명시적으로 변환하며 contract test로 누락을 검증합니다.

ID는 UUID string을 사용합니다. `null`, field 생략, 빈 배열의 의미를 OpenAPI에서 구분하고, 허용하지 않은 enum과 tenant field는 `400`으로 거부합니다.

### 4. 인증·권한·tenant

인증이 끝나면 Server는 최소한 다음 정보를 가진 `ActorContext`를 만듭니다.

```text
actor_id
company_id
roles
```

`request_id`와 `trace_id`는 actor identity가 아니라 요청 수명주기를 가진 `RequestContext`와 MDC에서 관리합니다.

- 쓰기 Request DTO에서 `company_id`를 받지 않습니다.
- 조회 filter에 `company_id`가 있더라도 인증 Context와 다른 값을 허용하지 않습니다.
- Repository query와 unique constraint에는 Company 범위를 포함합니다.
- 같은 Company에서 Role이 부족하면 `403 Forbidden`입니다.
- 다른 Company resource는 존재 여부를 감추기 위해 `404 Not Found`로 응답합니다.
- `VIEWER`는 조회만 가능하며 승인·수정·발송 Command를 실행할 수 없습니다.
- `AI_AGENT`는 감사 출처이지 로그인 Role이나 승인 주체가 아닙니다.

MVP의 tenant 격리 기준선은 서명된 JWT에서 만든 `ActorContext`, 모든 Repository의 `company_id` 범위 조건, tenant를 포함한 FK·UNIQUE 제약입니다. 어느 한 계층만 믿지 않고 Application과 DB 제약을 함께 적용합니다.

PostgreSQL RLS는 다음 조건을 모두 갖춘 후 defense-in-depth 계층으로 별도 ADR과 후속 migration에서 도입합니다.

- migration 계정과 분리된 `SUPERUSER`·`BYPASSRLS` 권한 없는 애플리케이션 DB 계정
- 신뢰된 `ActorContext`를 transaction마다 `SET LOCAL app.company_id`로 전달하는 연결 코드
- 로그인·Refresh Token·공개 Worker Link처럼 tenant를 찾기 전 요청의 bootstrap 방식
- connection pool 재사용 시 tenant context가 다음 요청으로 새지 않고, context 누락 시 fail-closed 되는 PostgreSQL 통합 테스트

RLS 도입 전후 모두 교차 사업장 연결은 tenant-aware 복합 FK·UNIQUE 제약으로 차단합니다. 로컬 H2 검증을 통과시키기 위해 `flyway.target`으로 최신 migration을 건너뛰지 않습니다.

Worker Link 원본 token은 한 번만 반환하고 DB에는 안전한 hash만 저장합니다. 만료시각, 허용 Task와 action, 회전·폐기 상태를 검증하고 승인된 Task의 최소 정보만 노출합니다.

Worker Link token은 URL path에 포함되므로 reverse proxy와 애플리케이션 access log에서 token segment를 반드시 redaction합니다. Audit에는 원본 token 대신 안전한 `worker_link_id`만 기록합니다.

AI Runtime 호출은 M3에서 TLS 위에 표준 `Authorization: Bearer <service-credential>`와 `X-Request-Id`, `traceparent`를 사용합니다. credential에는 AI Runtime용 audience/scope를 제한하고 환경 Secret으로 주입·회전하며 로그·오류·metric에 기록하지 않습니다. 요청에는 Server가 계산한 `remaining_deadline_ms`를 전달합니다. 배포 환경이 workload identity 또는 mTLS를 제공하면 `AiRuntimeClient` Port 뒤에서 교체할 수 있습니다. 별도 `Service-Authorization` custom header는 만들지 않습니다.

### 5. PII allow-list와 로그 금지값

AI 경계는 block-list가 아니라 allow-list로 만듭니다. 업무에 필요한 경우에만 다음과 같은 최소값을 전송할 수 있습니다.

- 내부 surrogate `worker_ref`
- Server가 생성한 별칭. 실제 표시 이름은 업무상 필수이고 AI Runtime·Provider 개인정보 정책을 충족할 때만 예외적으로 전송
- 선호 언어
- 업무상 필요한 날짜와 근무 상태
- 문서 종류·제출 상태·만료일
- Task/Workflow reference와 비식별 HR 입력

다음 값은 AI 요청, Domain Event, 일반 로그, metric label에 넣지 않습니다.

- 외국인등록번호, 여권번호, 계좌번호
- 전화번호, 이메일, 상세 주소
- JWT, Worker Link 원본 token, service credential, API Key
- 파일 원본과 접근 URL
- 전체 Prompt와 Provider 원문 응답

불가피한 사용자 원문은 전송 전에 redaction하고, 저장이 필요하면 목적·보존기간·접근권한을 별도 결정합니다.

### 6. Request ID, 동시성, 멱등성

- 외부 요청은 `X-Request-Id`를 검증하거나 새로 생성하고 응답·로그·오류·Domain Event에 연결합니다.
- AI 호출에는 동일한 request ID와 W3C `traceparent`를 전달합니다.
- Task, AiRun 등 충돌 가능한 aggregate는 version을 응답에 포함합니다.
- 중요한 Command는 body의 `expected_version`을 요구합니다.
- version이 다르면 `409 CONCURRENT_MODIFICATION`이며 Server가 자동으로 덮어쓰지 않습니다.
- MVP에서는 같은 목적의 `If-Match`와 `412 Precondition Failed`를 함께 제공하지 않습니다.

다음 endpoint는 `Idempotency-Key`를 필수로 사용합니다.

- `POST /api/v1/ai-runs`
- `POST /api/v1/ai-runs/{aiRunId}/retry`
- `POST /api/v1/ai-runs/{aiRunId}/candidate-decisions`
- Worker Link 발급·회전 Command
- `POST /api/v1/tasks/{taskId}/external-submissions`
- `POST /public/worker-links/{token}/responses`
- `POST /public/worker-links/{token}/documents`

```text
authenticated scope = company_id + HTTP method + canonical route template + target resource id + key_hash
public scope = worker_link_id + action + key_hash
```

- 원본 key는 요청 처리 중 hash한 뒤 로그와 DB에 저장하지 않습니다. resource 생성처럼 아직 target resource id가 없으면 scope에서 해당 값을 생략합니다.
- actor는 scope가 아니라 Audit metadata로 저장합니다. request hash에는 정규화된 path parameter와 validation이 끝난 body를 포함합니다.
- 같은 scope와 같은 request hash면 side effect를 다시 만들지 않고 같은 semantic result를 반환합니다.
- 같은 key에 다른 request hash면 `409 IDEMPOTENCY_CONFLICT`입니다.
- idempotency record와 업무 변경은 같은 transaction에 저장합니다.
- candidate에서 만든 Task는 `source_candidate_id` 같은 business unique constraint로 record 보존기간 이후의 중복도 막습니다.
- 원본 Worker Link token처럼 재생할 수 없는 Secret을 idempotency record에 저장하지 않습니다.
- Worker Link 발급·회전을 같은 key로 재요청하면 새 Link를 만들지 않고 `worker_link_id`, `expires_at`, `already_issued=true`, `worker_url=null`만 반환합니다.
- 첫 응답을 잃어 원본 token을 확인할 수 없다면 새 key로 명시적인 rotate Command를 호출합니다. 기존 Link를 폐기하고 새 token을 한 번만 반환합니다.
- 보존기간과 body 크기 제한은 endpoint OpenAPI와 운영 설정에 명시합니다.

### 7. 공통 오류 계약

Server External API는 다음 `snake_case` 구조를 사용합니다.

```json
{
  "timestamp": "2026-07-22T01:23:45Z",
  "status": 422,
  "code": "TASK_TRANSITION_NOT_ALLOWED",
  "message": "현재 상태에서는 요청한 작업을 수행할 수 없습니다.",
  "path": "/api/v1/tasks/example/approve",
  "request_id": "01-example-request-id",
  "field_errors": []
}
```

| HTTP | 의미 | 예시 code |
| ---: | --- | --- |
| `400` | JSON·형식·field validation 실패 | `VALIDATION_FAILED` |
| `401` | 인증 없음·만료·위조 | `AUTHENTICATION_REQUIRED` |
| `403` | 같은 tenant 안에서 Role·action 부족 | `ACCESS_DENIED` |
| `404` | resource 없음, 타 tenant 은닉, 유효하지 않은 public token | `RESOURCE_NOT_FOUND` |
| `405` | 지원하지 않는 HTTP method | `METHOD_NOT_ALLOWED` |
| `406` | 제공할 수 없는 응답 형식 | `NOT_ACCEPTABLE` |
| `409` | version·idempotency·unique 충돌 | `CONCURRENT_MODIFICATION` |
| `415` | 지원하지 않는 요청 media type | `UNSUPPORTED_MEDIA_TYPE` |
| `422` | 형식은 맞지만 Domain guard 위반 | `TASK_TRANSITION_NOT_ALLOWED` |
| `429` | rate limit | `RATE_LIMIT_EXCEEDED` |
| `500` | 예상하지 못한 Server 오류 | `INTERNAL_SERVER_ERROR` |
| `503` | DB·durable queue 등 요청 접수 자체가 불가능한 일시 장애 | `SERVICE_TEMPORARILY_UNAVAILABLE` |

오류 `code`는 대문자 `SNAKE_CASE`의 안정적인 Client 계약입니다. 공통 기술 오류만 `common`에 두고, Feature 오류는 `AuthErrorCode`, `TaskErrorCode`, `AiRunErrorCode`처럼 공통 `ApiErrorCode` 계약을 구현합니다. 하나의 거대한 `ErrorCode` enum에 모든 업무 오류를 추가하지 않습니다.

응답과 로그에 stack trace, SQL, Provider 응답 전문, Secret, 민감정보를 포함하지 않습니다. 사용자 메시지와 내부 진단 정보는 분리합니다.

Background AiRun의 circuit open·timeout·AI Runtime 5xx는 생성 요청에 Provider 오류를 그대로 전달하지 않습니다. 접수된 AiRun을 `RETRYING` 또는 `FAILED`로 영속하고 조회 API에서 안전한 `error_code`를 제공합니다.

### 8. Version compatibility 기록

모든 AiRun은 가능한 값만 임의 생성하지 않고 배포 설정과 AI Runtime 응답에서 검증하여 다음 정보를 저장합니다.

```text
backend_version
agent_version
model_provider / model_name / model_version
prompt_version
context_pack_version
workflow_catalog_version
contract_version / knowledge_version
request_id / trace_id / attempt_id
latency_ms / provider_attempt_count
parsing_error / validation_error / error_code
```

호환성은 배포 manifest의 검증된 조합으로 관리합니다.

| Version | Owner | Server 검증 | 불일치 처리 |
| --- | --- | --- | --- |
| `backend_version` | `server` | 실행 중인 build version 기록 | 관측 필드 |
| `contract_version` | `ai` | Server가 지원 목록에서 exact pin | AiRun `FAILED`, candidate 미저장 |
| `knowledge_version` | `knowledge` | 요청 pin/checksum과 Runtime 응답이 정확히 일치 | AiRun `FAILED`, candidate 미저장 |
| `workflow_catalog_version` | `knowledge` | Server projection과 Runtime 사용 version이 정확히 일치 | AiRun `FAILED`, candidate 미저장 |
| `agent_version` | `ai` | 선택한 release channel의 배포 allow-list와 일치 | AiRun `FAILED`, candidate 미저장 |
| `prompt_version`, `context_pack_version` | `ai`, `knowledge` | 선택한 agent release manifest 조합과 일치 | AiRun `FAILED`, candidate 미저장 |
| `model_provider`, `model_name`, `model_version` | `ai` | agent release manifest가 허용한 단일 tuple 또는 fallback set에 포함되는지 검증. Server가 모델을 선택하지 않음 | 누락·허용 밖 tuple은 AiRun `FAILED` |

`infra`의 Agent 배포 manifest가 검증된 `backend/agent/model/contract/knowledge/workflow/prompt/context` 조합을 기록하고 Server·AI 배포가 이를 pin합니다. Blue/Green은 서로 다른 검증 조합과 각 조합의 model fallback set을 동시에 allow-list할 수 있습니다. 문제가 생기면 개별 파일을 덮어쓰지 않고 이전에 검증한 manifest 조합으로 rollback합니다.

### 9. API 변경 규칙

optional response field 추가처럼 하위 호환인 변경은 `/api/v1`에서 허용합니다. strict Client가 처리하지 못하는 enum 값 추가, 기존 field 제거·이름 변경·의미 변경, 기존 Command 전이 축소는 breaking change이며 deprecation 기간 또는 새 major version과 ADR이 필요합니다. API 변경 PR은 OpenAPI·ObjectMapper serialization contract test·Client 영향과 필요한 Wiki·Notion mirror를 함께 갱신합니다. External API의 `snake_case` 설정이 AI Internal DTO에 암묵적으로 전파되지 않도록 경계 DTO와 contract test를 분리합니다.

## Consequences

### Positive

- Client가 인증·오류·동시성 실패를 일관되게 처리할 수 있습니다.
- 같은 기능의 중복 endpoint가 사라집니다.
- tenant 값 조작과 Worker Link 과다 노출을 Server에서 차단합니다.
- Server와 AI가 서로 다른 JSON naming을 사용해도 계약 경계가 명확합니다.

### Negative

- Command마다 version과 idempotency 처리가 필요합니다.
- 타 tenant 접근이 404이므로 운영 진단에는 request ID와 안전한 audit가 필요합니다.
- AI Internal API 변경 시 Consumer contract test가 필요합니다.

## Alternatives Considered

### 모든 API를 `/api/v1` 아래에 배치

Worker Link와 internal service 인증이 로그인 JWT API와 섞이므로 선택하지 않습니다.

### Request body의 `company_id`를 신뢰

Client 조작으로 tenant가 바뀔 수 있으므로 금지합니다. 인증 Context가 유일한 기준입니다.

### `PATCH /tasks/{id}`로 상태를 직접 수정

승인·증빙 guard와 audit가 우회되므로 명시적 Command endpoint를 사용합니다.

### AI 호출 HTTP Client의 자동 retry

Server durable retry와 AI Provider retry가 곱해질 수 있으므로 선택하지 않습니다. Retry 소유권은 ADR-0003에서 구분합니다.
