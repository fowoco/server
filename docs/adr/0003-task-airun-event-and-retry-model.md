# ADR-0003: Task·AiRun·Event·Retry 모델

- Status: Proposed
- Date: 2026-07-22
- Deciders: FOWOCO Server Team
- Related Issue: [#23](https://github.com/fowoco/server/issues/23)
- Supersedes: None

## Context

FOWOCO에는 서로 다른 세 가지 결과가 있습니다.

1. HR 업무가 어느 단계인지 나타내는 Task Workflow 상태
2. AI 호출이 기술적으로 실행됐는지 나타내는 AiRun 상태
3. 정상 분석 결과에서 정보 부족·사람 검토 필요를 나타내는 analysis outcome

이를 하나의 `status` enum으로 합치면 AI timeout 때문에 Task가 `FAILED`가 되거나, AI 호출 성공만으로 업무가 승인되는 오류가 생깁니다. 또한 Server와 AI Runtime 양쪽에서 자동 retry하면 실제 Provider 호출 횟수가 곱해집니다.

## Decision

### 1. 세 가지 상태를 분리

| 종류 | Owner | 값 |
| --- | --- | --- |
| Task Workflow | `server/task` | `DRAFT`, `NEEDS_INFO`, `READY_FOR_REVIEW`, `APPROVED`, `WAITING_WORKER`, `WAITING_EXTERNAL`, `COMPLETED`, `CANCELLED` |
| AiRun 실행 | `server/airun` | `QUEUED`, `RUNNING`, `RETRYING`, `SUCCEEDED`, `FAILED` |
| 분석 outcome | `ai` 계약, `server` 재검증 | `NEEDS_INFO`, `REVIEW_REQUIRED` |

`AiRun=SUCCEEDED + analysis_outcome=NEEDS_INFO`는 정상입니다. AI 호출과 Schema 검증은 성공했지만 업무 필수정보가 부족하다는 뜻입니다.

낮은 confidence, ambiguity, missing slot은 기술 실패로 만들지 않습니다. Transport 실패, deadline 초과, parsing 실패, Schema·version·request ID·핵심값 보존 검증 실패처럼 결과를 신뢰할 수 없을 때만 AiRun을 `FAILED`로 만듭니다.

### 2. Task 상태 의미

| 상태 | 의미 |
| --- | --- |
| `DRAFT` | HR이 내용을 작성·수정 중이며 검토 요청 전 |
| `NEEDS_INFO` | 필수 slot·문서·대상 정보가 부족함 |
| `READY_FOR_REVIEW` | 필수정보와 active ApprovalRequest snapshot을 갖춰 HR 승인 가능한 상태 |
| `APPROVED` | 특정 Task version과 snapshot을 권한 있는 HR/Admin이 승인함 |
| `WAITING_WORKER` | 승인된 안내·문서 요청을 전달하고 Worker 응답을 기다림 |
| `WAITING_EXTERNAL` | 외부기관 제출 reference를 남기고 결과를 기다림 |
| `COMPLETED` | Workflow의 필수 checklist·evidence·완료 guard를 충족함 |
| `CANCELLED` | 사유와 actor를 남기고 종료함 |

`COMPLETED`와 `CANCELLED`는 terminal입니다. 상태 변경은 Entity setter나 일반 PATCH가 아니라 Server의 명시적 Command와 guard로만 실행합니다.

### 3. Task command와 전이

| Command | 시작 상태 | 결과 상태 | 필수 guard |
| --- | --- | --- | --- |
| `createManualTask` | 없음 | 필수정보 부족 시 `NEEDS_INFO`, 충분하면 `DRAFT` | Company, Worker, Workflow reference 검증 |
| `acceptCandidate` | Task 없음 | `NEEDS_INFO` 또는 `READY_FOR_REVIEW` | HR/Admin, 동일 tenant, Run `SUCCEEDED`, candidate `PENDING`, expected version·idempotency, Server 재검증 |
| `provideInformation` | `NEEDS_INFO` | `NEEDS_INFO` 또는 `DRAFT` | 입력 권한, 필수값 재평가 |
| `requestReview` | `DRAFT`, `NEEDS_INFO` | `READY_FOR_REVIEW` | required slot·checklist 충족, active ApprovalRequest와 snapshot 생성 |
| `approve` | `READY_FOR_REVIEW` | `APPROVED` | HR/Admin, active ApprovalRequest, 현재 content revision·fingerprint 일치 |
| `reject` | `READY_FOR_REVIEW` | `DRAFT` | HR/Admin, 사유, 현재 승인 요청 종료 |
| `issueWorkerLink` | `APPROVED` | `WAITING_WORKER` | 유효 승인, link 범위·만료, 원자적 token hash 저장 |
| `rotateWorkerLink` | `WAITING_WORKER` | `WAITING_WORKER` | 기존 Link 폐기, 새 token hash·만료 저장, Task 상태 중복 변경 금지 |
| `recordWorkerFollowUp` | `WAITING_WORKER` | `READY_FOR_REVIEW` | `QUESTION`·`NOT_UNDERSTOOD` 등 재검토 필요 응답, 기존 승인 무효화와 새 ApprovalRequest snapshot. 단순 조회·upload는 자동 전이하지 않음 |
| `recordExternalSubmission` | `APPROVED`, `WAITING_WORKER` | `WAITING_EXTERNAL` | 유효한 현재 승인, 제출처·시각·안전한 외부 reference, Workflow별 evidence |
| `requestExternalCorrection` | `WAITING_EXTERNAL` | `READY_FOR_REVIEW` | 보완 사유, 기존 submission·evidence 보존, 새 ApprovalRequest snapshot |
| `complete` | `APPROVED`, `WAITING_WORKER`, `WAITING_EXTERNAL` | `COMPLETED` | Workflow별 필수 checklist·evidence 충족 |
| `cancel` | 모든 비종료 상태 | `CANCELLED` | 권한, 사유, actor |

`provideInformation`은 정보를 저장한 뒤 바로 승인 상태로 가지 않습니다. HR 수정 가능 상태인 `DRAFT`로 돌아가며, 별도 `requestReview` guard를 통과해야 합니다. AI candidate를 `ACCEPT`하면 Server가 필수정보를 재검증하여 `NEEDS_INFO` 또는 `READY_FOR_REVIEW` Task를 만듭니다. `READY_FOR_REVIEW`로 만들 때는 candidate decision과 같은 transaction에서 active ApprovalRequest와 `content_revision/fingerprint` snapshot도 생성합니다. Candidate 채택은 HR 승인과 다릅니다.

승인된 날짜·금액·대상 Worker·문서 종류·안내 내용처럼 중요 field가 변경되면 기존 승인을 무효화하고 `READY_FOR_REVIEW`로 되돌린 뒤 재승인을 요구합니다. 허용되지 않은 전이는 `422 TASK_TRANSITION_NOT_ALLOWED`, version 경쟁은 `409 CONCURRENT_MODIFICATION`입니다.

동시성 version과 승인 version을 구분합니다.

- `lock_version`: JPA `@Version`으로 모든 경쟁 update를 차단합니다.
- `content_revision`: 승인 대상 내용이 바뀔 때만 증가합니다.
- Approval은 `task_id + content_revision + fingerprint` snapshot에 묶입니다.

`READY_FOR_REVIEW`의 승인 대상 field가 바뀌면 현재 approval request를 종료하고 `DRAFT` 또는 `NEEDS_INFO`로 되돌립니다. `APPROVED` 또는 대기 상태에서 중요 field가 바뀌면 승인과 활성 Worker Link를 무효화하고, 필수정보가 충분하면 새 ApprovalRequest snapshot과 함께 `READY_FOR_REVIEW`, 부족하면 `NEEDS_INFO`로 전이합니다. 기존 응답·제출·Evidence·Audit은 삭제하지 않습니다.

active Worker Link는 Task가 `WAITING_WORKER`이고, 현재 `content_revision`의 유효 Approval과 Link가 pin한 revision이 모두 일치할 때만 유효합니다. `WAITING_WORKER`를 벗어나는 모든 Command, approval 무효화, `COMPLETED`, `CANCELLED`는 active Link를 같은 transaction에서 폐기합니다. `cancel`은 active ApprovalRequest를 종료하고 유효 Approval을 무효화합니다. `requestExternalCorrection`도 기존 ApprovalRequest·Approval을 종료·무효화한 뒤 새 snapshot을 만듭니다.

### 4. AiRun과 candidate lifecycle

AiRun 허용 전이는 다음과 같습니다.

| Trigger | 시작 상태 | 결과 상태 | 핵심 조건 |
| --- | --- | --- | --- |
| worker claim | `QUEUED`, `RETRYING` | `RUNNING` | deadline 확인, lease와 새 active Attempt 생성 |
| valid result | `RUNNING` | `SUCCEEDED` | active Attempt fencing과 모든 응답 검증 통과 |
| transient failure | `RUNNING` | `RETRYING` | Attempt 종료, budget·deadline 잔여, 다음 실행시각 저장 |
| terminal failure | `RUNNING`, `RETRYING` | `FAILED` | non-retryable, budget 소진 또는 deadline 만료 |
| deadline expired before claim | `QUEUED`, `RETRYING` | `FAILED` | AI Runtime 호출 없이 안전한 deadline error 저장 |
| manual retry | `FAILED` | `RETRYING` | HR/Admin, expected version, idempotency, manual budget |

worker claim 전에 `deadline_at`이 지났다면 AI Runtime을 호출하지 않고 `FAILED`로 종료합니다.

기본 순서는 다음과 같습니다.

```text
POST /api/v1/ai-runs
→ QUEUED
→ RUNNING
→ SUCCEEDED 또는 FAILED
```

실패한 Run의 수동 또는 명시적 Server retry는 다음과 같습니다.

```text
FAILED
→ RETRYING
→ RUNNING
→ SUCCEEDED 또는 FAILED
```

- retry는 새로운 AiRun을 만들지 않고 같은 AiRun에 새로운 `AiAttempt`를 추가합니다.
- Attempt는 `RUNNING → SUCCEEDED | FAILED | INTERRUPTED` lifecycle을 가지며 terminal 이후 수정·삭제하지 않습니다.
- `SUCCEEDED` Run의 valid candidate를 snapshot으로 저장합니다.
- candidate decision은 `PENDING`, `ACCEPTED`, `DISCARDED`로 추적합니다.
- 여러 candidate를 한 번에 결정하는 요청은 all-or-nothing transaction으로 처리합니다.
- 같은 candidate를 여러 번 ACCEPT해도 Task는 하나만 생성되도록 unique constraint와 idempotency를 적용합니다.
- Candidate 내용을 HR이 수정해 Task로 만들면 AI 원본과 HR 수정 snapshot을 모두 보존합니다.

`ACCEPT`와 `DISCARD`는 모두 `HR` 또는 `ADMIN`만 수행할 수 있습니다. `actor.company_id = ai_run.company_id = candidate.company_id`, Run `SUCCEEDED`, candidate `PENDING`, candidate가 해당 Run에 속함, `expected_version`, `Idempotency-Key`를 검증합니다. `ACCEPT`는 candidate decision, Task, 필요한 ApprovalRequest, AuditEvent, idempotency record, publication record를 같은 transaction에 저장합니다. `DISCARD`도 decision, 사유, AuditEvent, idempotency record, publication record를 같은 transaction에 저장하며 Task를 만들지 않습니다.

AiAttempt는 최소한 다음을 기록합니다.

```text
attempt_id / sequence
started_at / finished_at
request_id / trace_id
contract_version / required_knowledge_version
agent/model/prompt/context/workflow versions
provider_attempt_count / latency_ms
safe error category / parsing or validation error
```

### 5. Retry 소유권

| 계층 | 소유하는 retry | 소유하지 않는 retry |
| --- | --- | --- |
| Server `airun` | 영속 Attempt 생성, restart 복구, 사용자·정책 기반 retry, 전체 deadline과 budget | 한 HTTP 호출 안의 Provider fallback |
| Server `RemoteAiRuntimeClient` | timeout, circuit breaker, bulkhead, 오류 분류 | 투명한 다중 HTTP retry |
| AI Runtime | 하나의 AiAttempt 안에서 제한된 Provider retry·fallback | Server의 Run 상태와 사용자 retry |
| `reliability` | Domain Event/work item의 durable delivery | 분석 업무를 성공으로 바꾸는 retry |

`RemoteAiRuntimeClient`는 자동 retry를 하지 않습니다. Server가 새 AiAttempt를 영속한 뒤에만 AI Runtime을 다시 호출할 수 있습니다. Server는 현재 cycle의 `remaining_deadline_ms`를 전달하고 AI Runtime은 응답의 `provider_attempt_count`로 내부 호출 횟수를 알려줍니다. 배포 compatibility 설정은 `max_server_attempts × max_provider_attempts_per_attempt`의 최악 호출 수를 제한·기록합니다.

하나의 AiAttempt는 `AiRuntimeClient.analyze` 호출 정확히 한 번에 대응합니다. Server의 automatic retry도 먼저 현재 Attempt를 실패로 종료하고 `RETRYING`과 다음 실행시각을 영속한 뒤 새 Attempt로 실행합니다. 최초 요청의 `original_deadline_at`은 변경하지 않고, 각 automatic retry cycle은 `execution_deadline_at`, `max_automatic_attempts`, backoff 정책 snapshot을 가집니다. Manual retry는 `max_manual_retries`와 Audit guard를 통과한 뒤 같은 Run에 새 cycle deadline과 budget snapshot을 만듭니다.

다음과 같이 분류된 경우에만 Server automatic retry를 허용합니다.

- connection reset·timeout과 AI Runtime의 계약상 retryable `429`·`503`
- Server process 중단 또는 lease 만료
- 전체 deadline과 Attempt budget이 남아 있는 경우

S2S `401`·`403`, 잘못된 요청, parsing·Schema·version 불일치, 핵심값 손실, 금지 개인정보 탐지는 자동 retry하지 않고 `FAILED`로 기록합니다. `NEEDS_INFO`와 `REVIEW_REQUIRED`는 retry 대상이 아닌 정상 outcome입니다. HTTP status만으로 결정하지 않고 안정적인 내부 error classification을 사용합니다.

AiRun에는 `active_attempt_id`, monotonic `attempt_fence`, `lock_version`, `lease_owner`, `lease_expires_at`을 둡니다. Claim은 fence를 증가시켜 Attempt에 복사하고 lease heartbeat는 fence를 바꾸지 않습니다. 결과 확정 transaction은 `status=RUNNING`, `active_attempt_id=:attemptId`, `attempt_fence=:fence`를 모두 만족할 때만 Run과 candidate를 변경하며 `@Version`은 해당 final update의 동시 경쟁을 차단합니다. Lease가 끝난 뒤 늦게 도착한 stale response payload는 폐기하고 안전한 log·metric 또는 별도 append-only diagnostic만 남깁니다. Terminal Attempt는 수정하지 않습니다.

AiRun은 Domain invariant와 가능한 DB constraint로 다음을 보장합니다.

- `SUCCEEDED`: `analysis_outcome` 필수, 안전 검증을 통과한 candidate만 존재, `error_code` 없음
- `FAILED`: terminal `error_code` 필수, 미검증 candidate 없음
- `QUEUED`, `RUNNING`, `RETRYING`: `analysis_outcome` 없음
- `RUNNING`: active Attempt, lease, attempt fence 필수

### 6. Transaction과 외부 호출 경계

초기 HTTP 요청의 DB transaction 안에서 AI Runtime 응답을 기다리지 않습니다.

1. AiRun과 work item/event를 같은 DB transaction에 `QUEUED`로 저장합니다.
2. Commit 후 durable worker가 lease를 얻고 `RUNNING`과 AiAttempt를 기록합니다.
3. DB transaction 밖에서 AI Runtime을 호출합니다.
4. DB transaction 밖에서 JSON·PII·핵심값 검증을 끝냅니다.
5. 짧은 새 transaction에서 active Attempt fencing을 다시 확인하고 candidate snapshot, AiRun 상태, Audit/Event를 함께 저장합니다.

Task aggregate, Command record(Approval·Evidence·Submission), AuditEvent, idempotency record, publication/outbox record는 해당 Command의 같은 DB transaction에 저장합니다. 하나라도 실패하면 전체 rollback합니다. 외부 HTTP와 파일 I/O는 이 transaction 밖에서 수행합니다.

Worker Link 발급은 Task 전이, token hash, 만료·허용 action, Audit/Event를 한 transaction에 저장합니다. 원본 token은 commit 성공 후 한 번만 Client에 반환하며 로그에 남기지 않습니다.

### 7. Domain Event와 내구성

Server 내부 Domain Event는 다음 envelope를 사용합니다.

```text
event_id
event_type
payload_version
aggregate_type / aggregate_id
company_id
actor_type / safe actor_id
request_id / trace_id
occurred_at
allow-list payload
```

- DB 변경과 publication record를 같은 transaction에 저장합니다.
- handler는 `event_id`와 업무 unique key로 idempotent해야 합니다.
- 실패 record는 시도 횟수, 다음 실행시각, lease, 안전한 error code를 저장합니다.
- Server 재시작 뒤 미완료 record를 다시 처리합니다.
- 영구 실패를 성공으로 바꾸지 않고 운영 검토 대상으로 남깁니다.
- Event에는 JWT, Worker Link 원본 token, 민감 식별정보, 전체 Prompt를 넣지 않습니다.

MVP는 `DomainEventPublisher` Port 뒤의 **PostgreSQL-backed durable publication**을 사용합니다. #25의 작은 spike에서 Spring Modulith Event Publication Registry와 Spring Boot 4.1 호환성을 확인하고, 적합하면 해당 adapter를 사용하며 부적합하면 Transactional Outbox adapter를 구현합니다. Adapter 선택 때문에 Domain 코드를 바꾸지 않으며 Kafka·RabbitMQ를 필수로 도입하지 않습니다.

대표 event 이름은 과거형의 versioned business fact로 작성합니다.

```text
TaskCreated
TaskReviewRequested
TaskApproved
TaskApprovalInvalidated
WorkerLinkIssued
AiRunRequested
AiRunSucceeded
AiRunFailed
TaskCandidateAccepted
TaskCandidateDiscarded
```

Event handler가 다른 aggregate를 바꿔야 한다면 해당 모듈의 application Command를 호출합니다. 다른 모듈의 Entity를 직접 수정하지 않습니다.

## Consequences

### Positive

- AI 장애와 HR 업무 상태가 섞이지 않습니다.
- 후보 채택, Task 승인, 근로자 전달을 각각 감사할 수 있습니다.
- 재시작과 중복 전달에도 Run·Task·Link를 하나만 유지할 수 있습니다.
- 두 계층의 retry가 곱해져 Provider를 과도하게 호출하지 않습니다.

### Negative

- AiRun, AiAttempt, Candidate, Event publication 테이블이 필요합니다.
- 비동기 Client polling과 운영용 backlog 관측이 필요합니다.
- 상태 전이·중복·restart 경쟁을 통합 테스트해야 합니다.

## Alternatives Considered

### Task 상태에 `AI_RUNNING`, `AI_FAILED` 추가

기술 실행과 HR Workflow를 결합하고 수동 Task까지 AI 상태에 의존하게 되므로 선택하지 않습니다.

### AI 호출 성공 시 Task 자동 승인

Human-in-the-loop 원칙과 승인 snapshot을 우회하므로 금지합니다.

### Retry마다 새로운 AiRun 생성

하나의 사용자 요청에 대한 이력과 idempotency 추적이 끊기므로 같은 Run에 Attempt를 추가합니다.

### 외부 호출을 DB transaction 안에서 실행

긴 lock과 thread 고갈, 불명확한 rollback을 만들므로 외부 호출 전후를 짧은 transaction으로 분리합니다.

### MVP부터 Kafka 도입

현재 처리량보다 운영 복잡도가 크므로 DB-backed publication recovery를 먼저 사용합니다.
