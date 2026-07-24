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
transaction-local tenant context와 connection pool 비누수 테스트만 준비합니다.
아직 policy를 만들거나 RLS를 활성화하지 않습니다.

현재 `main`의 V1~V6에는 아래 12개 tenant table이 존재합니다. 기반 단계의 제한
role 테스트는 이 전체 범위에 업무 DML만 허용하고, table owner·DDL·`TRUNCATE`·
`REFERENCES` 권한과 RLS 우회 권한이 없음을 확인합니다.

- `company`, `user_account`, `refresh_token`
- `worker`, `worker_document`
- `task`, `task_checklist_item`, `task_transition_history`
- `approval_request`, `external_submission`, `task_evidence`, `audit_event`

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
2. 준비 migration에서 bootstrap 함수와 policy를 만들되 RLS는 켜지 않습니다.
3. tenant context와 bootstrap 호환 코드를 배포합니다.
4. #9에서 분리된 runtime role, 최소 GRANT와 Secret을 적용합니다.
5. RLS 비활성 상태에서 Login·Refresh·tenant A/B·connection pool 회귀 테스트를
   실행합니다.
6. 별도 forward migration으로 `ENABLE ROW LEVEL SECURITY`를 적용합니다.
7. 제한된 runtime role로 Smoke Test를 실행합니다.

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
