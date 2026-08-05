# PostgreSQL RLS 단계적 도입 가이드

이 문서는 [Issue #34](https://github.com/fowoco/server/issues/34)와
[ADR-0004](../adr/0004-postgresql-rls-tenant-isolation.md)의 실행 순서를 정리합니다.
RLS는 기존 `ActorContext`, Repository의 `company_id` 조건, tenant-aware DB 제약을
대체하지 않고 그 위에 DB 차단 계층을 추가합니다.

## 책임과 현재 범위

- #34는 tenant context adapter, PostgreSQL 전용 policy migration, 제한 role 격리
  테스트를 담당합니다.
- #9는 환경별 role 생성, 최소 GRANT, credential 발급·Secret 주입을 담당합니다.
- versioned migration에는 `CREATE ROLE`, 비밀번호, 환경별 실제 role 이름을 넣지
  않습니다.
- 대상 기능의 schema가 `main`에 병합되기 전에는 RLS migration 번호나 빈
  placeholder를 만들지 않습니다.

현재 기반 단계에서는 runtime/Flyway 설정 경계, PostgreSQL 전용 Flyway location,
transaction-local tenant context와 connection pool 비누수 테스트를 준비했습니다.
JWT로 인증된 Worker·Task·Approval·Audit 업무 transaction은 요청 값이 아니라
`ActorContext.companyId`를 transaction-local context의 신뢰 원본으로 사용합니다.
H2는 PostgreSQL custom setting을 흉내 내지 않고 transaction 경계만 검증합니다.
`V10`에서 공통 bootstrap 함수와 기존 tenant 테이블 policy를, `V13`에서 Worker Link
bootstrap 함수와 policy를, `V14`에서 AI 실행 테이블 policy를 생성했습니다. `V17`은
V16에서 추가한 직접 tenant 컬럼에 맞춰 Worker Link 업로드 policy를 단순화하고 업로드
멱등성 테이블 policy를 추가합니다. Worker Link bootstrap도 ACTIVE이면서 DB 시각 기준
미만료인 링크만 사업장을 반환하도록 제한합니다. RLS는 아직 활성화하지 않았습니다.

로그인·Refresh Token·Logout은 tenant context가 생기기 전 최소 bootstrap 조회가
필요합니다. Issue #34 작성 뒤 추가된 사업장 회원가입도 새 tenant 행을 처음 만드는
별도 bootstrap 흐름으로 함께 검토해야 합니다. Worker Link는 `V13`에서 같은 기준으로
확장했습니다.

`V16` 적용 후에는 `company_id`를 직접 보유한 아래 tenant table과, 부모 초안의 tenant를
따르는 `document_request_draft_type`이 존재합니다. 기반 단계의
제한 role 테스트는 이 전체 범위에 업무 DML만 허용하고, table owner·DDL·`TRUNCATE`·
`REFERENCES` 권한과 RLS 우회 권한이 없음을 확인합니다.

- `company`, `user_account`, `refresh_token`
- `worker`, `worker_document`, `stored_file`
- `task`, `task_checklist_item`, `task_transition_history`
- `approval_request`, `external_submission`, `task_evidence`, `audit_event`
- `event_publication`, `event_consumption`
- `document_request_draft`, `document_request_draft_type`
- `worker_link`, `worker_response`, `worker_response_upload`
- `worker_document_upload_idempotency`
- `ai_run`, `ai_attempt`, `ai_question`, `ai_candidate`

### V16 최초 배포 전제

`V16`은 Worker Link 자식 테이블의 `company_id`를 backfill하고 `NOT NULL`, 복합
`UNIQUE`, tenant-aware 복합 FK를 한 번에 적용합니다. 또한 `worker_document`의 Task
참조를 `(task_id, worker_id, company_id)` 복합 FK로 전환합니다.

이 migration은 pre-V16 애플리케이션이 같은 DB에 계속 쓰는 상황과
backward-compatible하지 않습니다. 현재는 운영 DB·운영 트래픽·구버전 Pod가 없는 최초
배포 전이므로 이 전제를 충족하며 expand-contract migration을 적용하지 않습니다. 이
전제를 충족하지 않는 환경에 적용할 때는 쓰기 중단 또는 expand-contract 절차를 먼저
설계해야 합니다.

기존 개발·테스트 데이터는 신뢰할 수 있는 부모 관계에서 사업장을 복원합니다.
`worker_response_upload`는 `worker_response`,
`worker_document_upload_idempotency`는 `worker_link`를 기준으로 backfill합니다. 복원한
사업장이 `stored_file.company_id`와 다르거나 NULL·orphan·교차 tenant 관계가 남으면
migration을 실패시키며, 임의 사업장으로 보정하거나 행을 삭제하지 않습니다.

`document_request_draft_type`에는 `company_id`가 없으므로 부모
`document_request_draft`의 `draft_id`와 현재 tenant context를 확인하는 `EXISTS`
policy를 사용합니다. 이 예외는 #57의 스키마와 JPA collection-table 계약을 유지하면서
자식 테이블 직접 접근도 격리하기 위한 것입니다.

`event_publication`은 여러 tenant의 미완료 row를 찾는 background queue이므로 일반
요청 table과 같은 policy를 바로 활성화하면 worker가 아무 이벤트도 claim하지 못할 수
있습니다. RLS 활성화 전 #34에서 “처리 가능한 `event_id + company_id`만 반환하는 최소
claim 함수” 또는 동등한 제한된 queue bootstrap 계약을 먼저 확정합니다. claim 뒤
handler·완료·실패 transaction은 event에 저장된 `company_id`를 tenant context로
설정하고 일반 policy를 따릅니다. Runtime role에 전체 Outbox RLS 우회 권한을 주지는
않습니다.

## 설정 계약

PostgreSQL `dev`·`prod` Profile은 같은 DB에 서로 다른 계정으로 연결합니다.

| 환경변수 | 용도 |
| --- | --- |
| `DB_URL` | 공통 PostgreSQL JDBC URL |
| `DB_RUNTIME_USERNAME`, `DB_RUNTIME_PASSWORD` | Spring Boot 업무 transaction |
| `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | Flyway DDL·policy migration |

runtime role은 `SUPERUSER`, `BYPASSRLS`, table owner, migration role membership,
DDL, `TRUNCATE`, `REFERENCES` 권한을 갖지 않습니다. 실제 값은 배포 환경 Secret에만
보관합니다.

여기서 DDL 차단은 공용·업무 schema를 변경할 수 없다는 뜻입니다. PostgreSQL의
기본 `PUBLIC` 권한으로 session-local 임시 table이 허용되는 환경에서는
`SECURITY DEFINER` 함수의 `search_path`를 신뢰하는 schema로 고정하고 `pg_temp`를
마지막에 둡니다. 임시 table 권한 자체를 회수할지는 #9의 database-level GRANT
정책에서 결정합니다.

## Staging 적용 순서

1. 대상 table과 tenant-aware FK·UNIQUE 제약이 `main`에 병합됐는지 확인합니다.
2. 인증된 업무 transaction이 `ActorContext.companyId`를 context로 설정하는지
   검증합니다.
3. 준비 migration에서 Login·Refresh·Outbox bootstrap 함수와 tenant 테이블 policy를 생성하되,
   RLS는 활성화하지 않습니다.
4. bootstrap 호환 코드를 배포합니다.
5. #9에서 분리된 runtime role, 최소 GRANT와 Secret을 적용합니다.
6. RLS 비활성 상태에서 Signup·Login·Refresh·tenant A/B·connection pool 회귀 테스트를
   실행합니다.
7. 별도 forward migration으로 `ENABLE ROW LEVEL SECURITY`를 적용합니다.
8. 제한된 runtime role로 Smoke Test를 실행합니다.

## Smoke Test

- Flyway `migrate`·`validate`가 성공하고 pending migration이 없습니다.
- runtime role은 `rolsuper = false`, `rolbypassrls = false`이며 대상 table의
  owner가 아닙니다.
- tenant context가 없거나 비어 있거나 UUID가 잘못되면 보호 table 접근이
  fail-closed 됩니다.
- A context에서 B 행의 조회·생성·수정·삭제가 차단됩니다.
- commit, rollback, 예외, timeout 뒤 같은 physical connection을 재사용해도 이전
  context가 남지 않습니다.
- Login·Refresh와 구현된 Worker Link 정상 흐름이 유지됩니다.
- Outbox claim이 다른 tenant payload를 노출하지 않고, claim된 event의 handler
  transaction이 해당 `company_id` context에서만 실행됩니다.
- 오류 응답과 일반 로그에 SQL, JWT, token, email, 개인정보가 노출되지 않습니다.

로컬 또는 CI PostgreSQL 기반 검증은 다음 환경변수를 사용합니다.

```text
POSTGRES_TEST_ENABLED=true
POSTGRES_TEST_URL=...
POSTGRES_TEST_USERNAME=...
POSTGRES_TEST_PASSWORD=...
```

통합 테스트는 이 계정으로 migration을 적용하고 테스트 수명 동안만 무작위 제한
role을 생성합니다. 따라서 격리 테스트 DB의 계정에는 role 생성 권한이 필요합니다.
테스트 role과 임시 비밀번호는 테스트 종료 시 제거되며 versioned migration이나
저장소에 남지 않습니다.

## 장애와 forward-only 복구

1. 배포 진행을 중단하고 `request_id`로 영향 범위를 확인합니다.
2. policy 오류는 기존 migration을 수정하지 않고 새 forward migration으로
   교정합니다.
3. 전체 업무가 중단되는 긴급 상황에서만 승인된 담당자가 새 migration으로 대상
   table의 RLS를 일시 비활성화합니다.
4. 비활성화 중에도 Repository의 `company_id` 조건과 tenant-aware DB 제약은
   유지합니다.
5. 원인을 수정한 뒤 새 migration으로 RLS를 재활성화하고 Smoke Test를 반복합니다.

공유 DB에서 `flywayClean`, schema history 수동 조작, 적용된 migration 수정 또는
checksum 은폐 목적의 `flyway repair`는 사용하지 않습니다.
