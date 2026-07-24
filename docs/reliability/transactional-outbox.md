# Transactional Outbox 운영 가이드

## 한눈에 보기

Transactional Outbox는 “DB 저장은 성공했는데 후속 이벤트가 사라지는 문제”를
막습니다. 업무 데이터와 발행할 이벤트를 같은 transaction에 저장한 뒤, 별도 worker가
안전하게 처리합니다.

```text
Task 생성·취소
→ Task·Audit·event_publication을 한 번에 commit
→ Outbox worker가 처리할 row를 lease
→ 각 handler 실행
→ event_consumption으로 handler 완료를 기록
→ event_publication을 COMPLETED로 종료
```

Kafka나 RabbitMQ가 필요한 구조는 아닙니다. MVP는 기존 PostgreSQL을 내구성 저장소로
사용하고, 호출 코드는 `DomainEventPublisher` Port에만 의존합니다.

## 테이블을 쉽게 이해하기

| 테이블 | 의미 |
| --- | --- |
| `event_publication` | 처리해야 할 이벤트와 현재 상태, 시도 횟수, 다음 시각, lease를 저장 |
| `event_consumption` | 어느 handler가 어느 이벤트를 이미 성공했는지 저장 |

`event_publication`의 상태는 다음과 같습니다.

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 아직 처리하지 않음 |
| `PROCESSING` | 특정 서버가 제한 시간 동안 처리 권한을 가짐 |
| `RETRY_WAIT` | 일시 실패 후 다음 처리 시각을 기다림 |
| `COMPLETED` | 모든 handler 처리가 끝남 |
| `REVIEW_REQUIRED` | 자동 처리를 멈추고 개발자·운영자 확인이 필요함 |

## 새 이벤트를 발행하는 방법

1. 기능 모듈에서 과거형 업무 사실 이름을 정합니다. 예: `TaskCreated`.
2. payload field를 상수 allow-list로 선언합니다.
3. `DomainEventEnvelope`를 만들고 application service의 기존 `@Transactional`
   method 안에서 `DomainEventPublisher.publish()`를 호출합니다.
4. 업무 저장, Audit, Event 중 하나라도 실패하면 전체가 rollback되는 통합 테스트를
   작성합니다.

transaction 밖에서 `publish()`하면 서버가 즉시 거부합니다. 이벤트를 먼저 commit한 뒤
업무 저장을 따로 수행하면 원자성이 깨지므로 금지합니다.

## 새 handler를 추가하는 방법

`DomainEventHandler`를 구현한 Spring Bean을 기능 모듈의 infrastructure 또는
application adapter에 둡니다.

- `handlerName()`은 배포 뒤 의미를 바꾸지 않는 고유 이름을 사용합니다.
- `supports(eventType)`으로 처리할 이벤트를 명시합니다.
- `handle(event)`는 다른 모듈 Entity를 직접 수정하지 않고 해당 모듈의 application
  command 또는 Port를 호출합니다.
- handler의 DB 변경과 `event_consumption` 저장은 같은 transaction입니다.
- 외부 API는 상대 시스템에도 `event_id` 또는 별도 업무 unique key를 idempotency
  key로 전달해야 합니다. DB 완료 기록만으로 상대 시스템의 중복 실행까지 되돌릴 수는
  없습니다.
- 일시 오류는 `RetryableEventHandlingException`, 입력·계약 오류처럼 반복해도
  성공하지 않는 오류는 `NonRetryableEventHandlingException`으로 분류합니다.

이미 완료된 `(event_id, handler_name)`은 재전달 시 건너뜁니다. handler 이름을
변경하면 새 handler로 인식되므로 단순 refactoring 때 이름을 바꾸지 않습니다.

## 개인정보와 로그 규칙

Event payload는 `SafeEventPayload.of(allowedFields, values)`를 통과해야 합니다.

- 필요한 작은 업무값만 넣습니다. 예: `status`, `workflow_id`, `task_type`.
- Worker 이름·이메일·전화번호·여권번호·외국인등록번호·계좌번호를 넣지 않습니다.
- JWT, Worker Link 원본 token, 비밀번호, API Key, 전체 Prompt를 넣지 않습니다.
- 큰 객체, 중첩 JSON, Entity 전체를 넣지 않습니다.
- 실패 로그에는 payload나 예외 원문 대신 `event_id`, `event_type`, 안전한
  `error_code`, 시도 횟수만 기록합니다.

## 기본 설정

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `OUTBOX_ENABLED` | `true` | scheduler 실행 여부 |
| `OUTBOX_POLL_INTERVAL` | `1s` | 처리할 이벤트를 확인하는 간격 |
| `OUTBOX_BATCH_SIZE` | `20` | 한 번에 lease할 최대 row 수 |
| `OUTBOX_LEASE_DURATION` | `30s` | 한 서버의 처리 권한 유효시간 |
| `OUTBOX_MAX_ATTEMPTS` | `8` | 자동 시도 한도 |
| `OUTBOX_INITIAL_BACKOFF` | `1s` | 첫 재시도 대기시간 |
| `OUTBOX_MAX_BACKOFF` | `5m` | 재시도 대기시간 상한 |

lease는 정상 handler 최대 처리시간보다 길어야 합니다. 값을 줄이기 전에 느린 handler와
외부 API timeout을 확인합니다.

## 장애 확인

Actuator가 노출되는 내부 운영 환경에서는 다음 Micrometer 지표를 확인합니다.

- `fowoco.outbox.publications.backlog`: 미완료 이벤트 수
- `fowoco.outbox.publications.oldest.delay.seconds`: 가장 오래된 미완료 이벤트 지연
- `fowoco.outbox.publications.processed{result=completed|retry|review_required}`:
  처리 결과 누적 수

DB에서는 payload를 출력하지 않고 상태와 안전한 오류만 확인합니다.

```sql
SELECT event_id, company_id, event_type, status, attempt_count,
       next_attempt_at, lease_owner, lease_expires_at, last_error_code, updated_at
FROM event_publication
WHERE status <> 'COMPLETED'
ORDER BY occurred_at;
```

`PROCESSING` lease가 만료되면 다음 poll에서 자동 복구됩니다. `RETRY_WAIT`도
`next_attempt_at` 이후 자동 처리됩니다. `REVIEW_REQUIRED`는 원인을 수정했다고 해서
DB를 임의로 `PENDING`으로 바꾸지 않습니다. 별도 관리 command와 감사로그가 구현되기
전에는 담당 개발자가 원인을 확인하고 forward migration 또는 후속 Issue로 복구합니다.

`OUTBOX_ENABLED=false`는 자동 처리를 멈출 뿐 새 이벤트 저장을 막지 않습니다. 장애 중
이벤트가 계속 누적될 수 있으므로 backlog를 함께 관찰하고, 수정 배포 후 다시 활성화해
순서대로 처리합니다.

## 테스트 기준

- H2 통합 테스트: 업무 변경·이벤트 원자성, 재시도 rollback, 중복 전달, lease 만료
  복구, 수동 검토 전환
- PostgreSQL CI 테스트: V7 migration, 상태 CHECK, tenant-aware FK, handler unique
  constraint, index
- 기능 통합 테스트: 실제 command가 올바른 event type과 최소 payload를 발행하는지
  검증

로컬 전체 검증:

```bash
./gradlew clean test
```

CI는 PostgreSQL 17 service에도 모든 Flyway migration을 적용하고 계약을 검증합니다.
