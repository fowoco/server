# ADR-0004: PostgreSQL RLS 기반 tenant 2차 격리와 안전한 도입

- Status: Proposed
- Date: 2026-07-23
- Deciders: FOWOCO Server Team
- Related Issue: [#34](https://github.com/fowoco/server/issues/34)
- Related ADRs: [ADR-0001](0001-repository-and-module-boundaries.md), [ADR-0002](0002-api-security-and-error-contract.md)
- Supersedes: None

## Context

FOWOCO의 기본 tenant 격리는 다음 세 계층을 함께 사용합니다.

```text
서명된 JWT 또는 검증된 Worker Link
→ Server ActorContext와 Role 검사
→ company_id 범위 Repository query
→ tenant-aware FK·UNIQUE·CHECK
```

이 기준선은 유지합니다. PostgreSQL RLS(Row Level Security)는 Application query에서
`company_id` 조건이 실수로 누락되더라도 다른 사업장의 행을 한 번 더 차단하는
defense-in-depth 계층입니다. RLS가 Application 권한 검사, Repository 범위 조건,
Domain guard 또는 tenant-aware DB 제약을 대신하지 않습니다.

현재 `main`에는 V2의 `company`, `user_account`, `refresh_token`이 존재합니다.
Worker·Document metadata를 소유한 Issue #5의 V3는 아직 병합되지 않았습니다.
ADR-0001에 따라 선행 migration이 병합되기 전에 후행 번호를 Ready 또는 merge할 수
없으며, Issue #34가 Worker·Task·Document 테이블을 미리 생성하지 않습니다.

RLS를 정책 SQL만으로 먼저 활성화하면 다음 문제가 생길 수 있습니다.

- 로그인은 email로 사용자를 찾기 전에는 `company_id`를 알 수 없습니다.
- Refresh Token과 Worker Link도 token hash를 찾기 전에는 tenant를 알 수 없습니다.
- `SET`으로 설정한 session 값이 connection pool의 다음 요청에 남을 수 있습니다.
- Flyway와 runtime이 같은 table owner 또는 superuser 계정을 사용하면 RLS 검증이
  실제 애플리케이션 권한을 재현하지 못합니다.
- PostgreSQL 전용 RLS SQL을 H2 공통 migration에 섞거나 `flyway.target`으로
  건너뛰면 local/test가 최신 공통 schema를 검증하지 못합니다.
- RLS 활성화와 호환 코드 배포를 한 단계로 처리하면 로그인·재발급·공개 링크가
  동시에 중단될 수 있습니다.

따라서 RLS 정책, DB role, transaction tenant context, bootstrap 조회, migration
location, 검증과 rollback을 하나의 도입 계약으로 결정합니다.

## Decision

### 1. RLS는 기존 tenant 격리 위에 추가한다

- `ActorContext.companyId` 또는 검증된 Worker Link에서 얻은 `company_id`만 신뢰합니다.
- Request body, query parameter, header의 임의 `company_id`를 tenant context로
  사용하지 않습니다.
- 모든 business Repository는 RLS 도입 뒤에도 resource ID와 `company_id`를 함께
  조건으로 사용합니다.
- 교차 사업장 연결은 RLS가 아니라 tenant-aware 복합 FK·UNIQUE와 Domain guard로도
  차단합니다.
- RLS 정책은 테이블의 tenant 소유권을 명확하게 판정할 수 있을 때만 추가합니다.
  아직 존재하지 않거나 tenant 관계가 모호한 테이블을 Issue #34에서 선행 생성하거나
  임의로 변경하지 않습니다.
- 새 tenant table을 소유한 Feature Issue는 `company_id` 및 tenant-aware 제약을
  먼저 제공하고, RLS는 별도의 후속 migration에서 활성화합니다.

RLS는 PostgreSQL에서만 보장되는 보안 계층입니다. H2는 빠른 local/unit 보조
환경으로 유지하되 RLS 동작의 합격 기준으로 사용하지 않습니다.

### 2. DB 관리·migration·runtime 권한을 분리한다

환경마다 실제 login role 이름과 credential은 다를 수 있으며 Git, migration,
Wiki, Issue, PR에 실제 값을 기록하지 않습니다. 다음은 권한의 논리적 책임입니다.

| 책임 | 용도 | 허용 | 금지 |
| --- | --- | --- | --- |
| DB provisioning | database와 login role 준비, credential 주입 | role 생성·회전, 소유권 준비 | 애플리케이션 실행 |
| Flyway migration | versioned DDL, policy와 bootstrap 함수 관리 | schema 변경과 migration 검증 | HTTP 요청 처리 |
| Application runtime | 일반 업무 transaction | 필요한 table DML, 제한된 함수 실행 | `SUPERUSER`, `BYPASSRLS`, table owner, DDL |

Application runtime role에는 다음 규칙을 적용합니다.

- `SUPERUSER`, `BYPASSRLS`, table owner 또는 migration role membership을 주지 않습니다.
- 필요한 `SELECT`, `INSERT`, `UPDATE`, `DELETE`와 제한된 bootstrap 함수
  `EXECUTE`만 부여합니다.
- `CREATE`, `ALTER`, `DROP`, `TRUNCATE`, `REFERENCES` 권한을 부여하지 않습니다.
  `TRUNCATE`와 `REFERENCES`는 RLS 대상이 아니므로 GRANT 단계에서 차단합니다.
- 애플리케이션이 migration role로 fallback하지 않도록 credential 누락 시 시작을
  실패시킵니다.

Spring Boot runtime DataSource는 `spring.datasource.*`, Flyway 전용 DataSource는
`spring.flyway.url`, `spring.flyway.user`, `spring.flyway.password`에 대응하는
외부 환경변수로 분리합니다. `.env.example`에는 변수 이름과 설명만 기록합니다.

환경별 role 생성·credential·role membership은 #9의 배포 책임입니다. Flyway
migration에는 `CREATE ROLE`, `ALTER ROLE ... PASSWORD`, 실제 username 또는
password를 넣지 않습니다.

### 3. tenant context는 같은 transaction과 connection에만 설정한다

인증된 업무 요청은 Spring transaction이 시작된 뒤, 해당 transaction의 첫 tenant
table query 전에 같은 JDBC connection에서 tenant context를 설정합니다.

동적 값을 SQL 문자열로 조합하지 않고 parameter binding 가능한 다음 형태를
사용합니다.

```sql
SELECT pg_catalog.set_config('app.company_id', ?, true);
```

세 번째 인자 `true`는 `SET LOCAL`과 같은 transaction-local 의미입니다.

- Servlet Filter처럼 transaction 밖일 수 있는 위치에서 설정하지 않습니다.
- 일반 `SET app.company_id = ...`를 사용하지 않습니다.
- transaction 없는 tenant Repository 접근은 context가 없으므로 fail-closed 됩니다.
- 중첩 transaction과 새 transaction은 각각 context를 설정합니다.
- background handler도 event에 저장된 안전한 `company_id`를 다시 검증한 뒤 같은
  규칙으로 설정합니다.

RLS policy가 읽는 기준식은 다음과 같습니다.

```sql
NULLIF(pg_catalog.current_setting('app.company_id', true), '')::uuid
```

- context가 없거나 빈 값이면 식은 `NULL`이 되어 어떤 tenant 행도 허용하지 않습니다.
- UUID가 아닌 값은 cast 오류로 실패하며 우회하지 않습니다.
- 직접 `company_id`가 있는 테이블은 가능한 한 현재 행의 값만 비교합니다.
- 다른 테이블을 조회하는 policy subquery는 성능·동시성·정보 노출 위험이 있으므로
  직접 tenant 판정이 불가능하고 소유 Feature와 합의한 경우에만 사용합니다.

### 4. 정책은 runtime role이 모든 DML에서 같은 tenant만 다루게 한다

RLS 대상 table은 정책을 만든 뒤 `ENABLE ROW LEVEL SECURITY`를 적용합니다.
정책은 환경별 login role 이름에 의존하지 않도록 `PUBLIC`에 적용하되, RLS 자체는
권한을 부여하지 않으므로 실제 접근 가능 여부는 최소 GRANT로 제한합니다.

기본 tenant policy는 다음 의미를 가집니다.

```sql
USING (
    company_id =
    NULLIF(pg_catalog.current_setting('app.company_id', true), '')::uuid
)
WITH CHECK (
    company_id =
    NULLIF(pg_catalog.current_setting('app.company_id', true), '')::uuid
)
```

- `SELECT`, `UPDATE`, `DELETE`의 기존 행은 `USING`으로 제한합니다.
- `INSERT`, `UPDATE`의 새 행은 `WITH CHECK`로 제한합니다.
- policy와 constraint에는 읽을 수 있는 명시적 이름을 사용합니다.
- 같은 목적의 policy를 여러 개 만들어 permissive `OR` 결합으로 범위를 넓히지
  않습니다.

첫 도입에서는 `FORCE ROW LEVEL SECURITY`를 사용하지 않습니다. Runtime role이
table owner가 아님을 catalog와 통합 테스트로 강제하고, migration owner의
versioned DDL·data correction과 제한된 bootstrap 함수가 의도적으로 동작하게
합니다. 향후 table owner credential이 runtime에 노출될 가능성이나 운영 요구가
생기면 bootstrap 함수 owner와 migration 절차를 먼저 분리한 새 ADR/migration에서
`FORCE`를 검토합니다.

### 5. bootstrap은 최소 `SECURITY DEFINER` 함수로 company만 찾는다

로그인·Refresh Token·Worker Link는 tenant context 설정 전에 식별값을 lookup해야
합니다. Runtime role에 전체 auth/link table의 RLS 우회 SELECT 권한을 주지 않고,
정확한 입력으로 `company_id` 하나만 반환하는 최소 bootstrap 함수를 사용합니다.

| 흐름 | 입력 | 반환 | 이후 처리 |
| --- | --- | --- | --- |
| Login | 정규화된 email | `company_id` 또는 없음 | context 설정 후 user/company 재조회와 비밀번호 검증 |
| Refresh/Logout | 원문이 아닌 token hash | `company_id` 또는 없음 | context 설정 후 token family lock·회전·폐기 |
| Worker Link | 원문이 아닌 token hash | `company_id` 또는 없음 | context 설정 후 만료·폐기·허용 action 검증 |

Bootstrap 함수는 다음 규칙을 모두 지킵니다.

- table owner 또는 별도 최소 함수 owner의 `SECURITY DEFINER`로 실행합니다.
- SQL object를 schema-qualified name으로 참조하고, 신뢰할 수 있는 schema와
  `pg_temp`를 마지막에 둔 고정 `search_path`를 사용합니다.
- dynamic SQL을 사용하지 않습니다.
- `password_hash`, token row, 사용자 상태, Worker 개인정보를 반환하지 않습니다.
- 생성 직후 `PUBLIC`의 기본 `EXECUTE`를 회수하고 runtime role에 필요한 함수만
  선택적으로 허용합니다.
- email과 token hash를 일반 로그·오류·metric에 남기지 않습니다.
- 존재·상태 차이를 외부 오류로 구분하지 않고 기존 Auth/Worker Link 오류 계약을
  유지합니다.

함수가 `company_id`를 반환하면 같은 transaction에서 tenant context를 설정한 뒤
기존 tenant-aware Repository와 Domain 검증을 수행합니다. Bootstrap 함수가 인증,
Role, token 만료 또는 Worker Link action을 승인하지 않습니다.

Worker Link bootstrap의 실제 함수와 통합 테스트는 #7의 table과 token 계약이
확정된 뒤 추가합니다. Issue #34가 Worker Link table을 미리 만들지 않습니다.

운영 runtime에서는 Demo Seed를 비활성화합니다. PostgreSQL demo seed가 필요하면
#9의 명시적인 provisioning 단계에서 migration/provisioning credential로
실행하며 runtime role에 tenant 전체 생성 권한을 추가하지 않습니다.

### 6. 공통 migration과 PostgreSQL 전용 migration을 분리한다

Flyway location은 다음과 같이 분리합니다.

```text
classpath:db/migration               # H2와 PostgreSQL 공통
classpath:db/migration-postgresql    # PostgreSQL 전용 RLS·policy·함수
```

- H2 `local`/`test`는 공통 location만 사용합니다.
- PostgreSQL `dev`/`prod`와 migration 통합 테스트는 두 location을 모두 사용합니다.
- H2 호환을 이유로 PostgreSQL RLS SQL을 약화하지 않습니다.
- `flyway.target`, migration 실행 skip 또는 빈 migration으로 차이를 숨기지 않습니다.
- 공통 migration은 H2에서도 최신까지 적용하고 `validate`와 pending 없음 상태를
  확인합니다.
- PostgreSQL 전용 migration도 실제 제한된 role 테스트에서 `migrate`, `validate`,
  pending 없음 상태를 확인합니다.

후행 migration은 의존하는 선행 migration이 `main`에 병합되어 실제 schema가
확정되기 전에 파일이나 빈 placeholder로 예약하지 않습니다. 구현 직전에 최신
`main`의 migration 목록과 진행 중인 PR을 확인하고, 아직 사용되지 않은 다음 번호를
선택합니다. 특정 Issue에 migration 번호를 영구적으로 고정하지 않으며, 실제 의존
관계와 병합 순서를 기준으로 번호를 확정합니다.

활성화는 두 단계의 expand-and-enforce 방식으로 진행합니다.

1. **준비 migration**: bootstrap 함수와 policy를 만들되 아직 RLS를 활성화하지
   않습니다.
2. **호환 코드·role 배포**: tenant context adapter, bootstrap 호출, 분리된 runtime
   credential과 최소 GRANT를 적용하고 RLS 비활성 상태에서 회귀 테스트합니다.
3. **활성화 migration**: 별도 forward migration에서 대상 table의 RLS를
   활성화합니다.

따라서 현재 예상 번호가 그대로 유지되면 V4는 준비 migration이고, 실제 활성화는
그 다음 사용 가능한 번호가 됩니다. 번호와 병합 순서는 V3와 #9 담당자에게 알리고
PR에서 함께 검토합니다.

### 7. 실제 제한된 PostgreSQL role로 검증한다

Migration/owner connection만 사용한 테스트는 RLS 합격 기준이 아닙니다.
PostgreSQL 통합 테스트는 schema를 준비하는 migration connection과 요청을
실행하는 제한된 runtime connection을 분리합니다.

최소 검증 항목은 다음과 같습니다.

#### Role과 schema

- runtime role의 `rolsuper = false`, `rolbypassrls = false`
- runtime role이 대상 table owner가 아니고 migration role member도 아님
- runtime role에 DDL·`TRUNCATE`·`REFERENCES` 권한이 없음
- 대상 table에 policy가 존재하고 활성화 단계 이후 RLS가 enabled 상태
- Flyway `migrate`·`validate` 성공과 pending migration 없음

#### Tenant 격리

- A context의 raw SQL에서 Repository의 `company_id` 조건을 일부러 생략해도 B 행은
  조회되지 않음
- A context에서 B tenant 행 `INSERT`와 tenant 변경 `UPDATE`가 실패함
- A context에서 B 행 `UPDATE`·`DELETE`가 0행 처리됨
- context 누락·빈 값·잘못된 UUID에서 보호 table 접근이 fail-closed 됨
- tenant-aware FK·UNIQUE가 교차 사업장 연결을 별도로 차단함

#### Bootstrap과 정상 흐름

- Login email과 Refresh Token hash bootstrap이 company만 결정함
- runtime role이 bootstrap 함수 밖에서 다른 tenant의 auth row를 읽지 못함
- 기존 로그인·Refresh rotation·logout이 RLS 활성화 뒤에도 동작함
- Worker Link 구현 뒤 정상·만료·폐기·변조 token을 같은 정책으로 검증함

#### Connection pool

- 같은 connection을 A→B 순서로 재사용해도 A context가 B 요청에 남지 않음
- commit, rollback, exception, timeout 뒤 다음 transaction에 context가 남지 않음
- transaction 없는 Repository 호출이 테스트에서 발견됨
- background transaction도 요청 transaction과 같은 격리 규칙을 사용함

H2 테스트는 공통 migration과 Application 회귀를 빠르게 확인하는 보조 수단이며,
위 RLS 보안 검증을 대체하지 않습니다.

### 8. 차단 결과와 관측을 구분한다

RLS는 모든 차단을 SQL 오류로 반환하지 않습니다.

- 다른 tenant의 `SELECT`, `UPDATE`, `DELETE`는 0행으로 보일 수 있으며 외부에는
  기존 계약대로 안전한 `404 RESOURCE_NOT_FOUND` 또는 멱등 결과를 사용합니다.
- `WITH CHECK` 위반이나 DB 권한 오류는 안정적인 내부 error code와 `request_id`로
  추적하되 SQL, policy 식, JWT, token, email, 개인정보를 응답·일반 로그에 넣지
  않습니다.
- RLS가 차단했다는 이유로 타 tenant resource의 존재 여부를 외부에 노출하지
  않습니다.
- metric label에 `company_id`, user/worker ID 또는 request ID를 넣지 않습니다.

### 9. 배포와 rollback은 forward-only로 수행한다

Staging 도입 순서는 다음과 같습니다.

1. 이 ADR을 `Accepted`로 확정합니다.
2. V3와 대상 table의 최종 tenant 제약을 확인합니다.
3. 준비 migration을 적용합니다.
4. tenant context·bootstrap 호환 코드와 분리된 runtime role을 배포합니다.
5. RLS 비활성 상태에서 Login·Refresh·tenant A/B·pool 회귀 테스트를 실행합니다.
6. 별도 활성화 migration으로 RLS를 켭니다.
7. 제한된 runtime role로 Smoke Test와 connection 재사용 테스트를 실행합니다.
8. Worker·Task·Document·Worker Link table이 추가될 때 같은 절차의 후속 migration으로
   범위를 확장합니다.

문제가 생기면 적용된 migration을 수정·삭제하거나 `flyway repair`로 checksum
차이를 숨기지 않습니다.

- 트래픽 또는 배포 진행을 중지하고 `request_id`로 영향 범위를 확인합니다.
- 정책 오류이면 새 forward migration으로 policy를 교정합니다.
- 로그인·전체 업무가 중단되는 긴급 상황이면 승인된 runbook에 따라 새 migration으로
  대상 table의 RLS를 일시 비활성화합니다.
- 이 경우에도 Repository의 `company_id` 조건과 tenant-aware DB 제약은 유지하며,
  원인 수정과 재활성화 migration을 즉시 후속 처리합니다.
- credential 노출이나 과도한 role membership이면 해당 credential을 폐기·회전하고
  최소 권한을 다시 적용합니다.

공유 persistent DB를 `flywayClean`하거나 수동으로 schema history를 조작하지
않습니다.

### 10. 작업 소유권과 완료 경계를 분리한다

| 범위 | 소유 |
| --- | --- |
| RLS ADR, tenant context adapter, PostgreSQL policy migration과 격리 테스트 | #34 |
| PostgreSQL role provisioning, Secret 주입, staging·Smoke·rollback runbook | #9와 #34 공동 검토 |
| Worker·Document tenant column과 FK·UNIQUE | #5·#13 |
| Task tenant column과 FK·UNIQUE | #6 |
| Worker Link token·tenant bootstrap 대상 table | #7 |

Issue #34는 준비 작업을 V3와 병렬로 진행할 수 있지만 다음 항목 전에는 RLS 활성화
migration을 Ready 또는 merge하지 않습니다.

- 관련 ADR이 `Accepted`
- V3와 실제 대상 schema가 `main`에 병합됨
- migration/runtime credential 분리와 최소 GRANT가 staging에 준비됨
- Login·Refresh bootstrap과 tenant context adapter가 PostgreSQL 테스트를 통과함
- 제한된 runtime role의 A/B 격리와 connection pool 누수 테스트가 통과함
- Worker Link를 RLS 범위에 포함한다면 #7 bootstrap 계약이 준비됨

## Consequences

### Positive

- Application query 실수에도 PostgreSQL이 타 사업장 행을 한 번 더 차단합니다.
- Runtime credential 탈취 시 superuser/table owner보다 피해 범위가 작습니다.
- `SET LOCAL` 범위와 pool 누수 테스트가 명시되어 요청 간 tenant 혼합을 방지합니다.
- 로그인·Refresh·Worker Link bootstrap이 일반 tenant table 접근 권한과 분리됩니다.
- H2 편의를 위해 PostgreSQL 보안을 약화하거나 migration을 건너뛰지 않습니다.
- 준비와 활성화를 분리해 로그인 전체 중단 전에 호환성을 검증할 수 있습니다.

### Negative

- Flyway와 runtime credential, migration location과 배포 순서가 늘어납니다.
- Bootstrap 함수는 매우 작은 SQL이라도 보안 검토와 권한 테스트가 필요합니다.
- RLS가 0행으로 차단한 경우 Application 오류와 구분하기 어려워 운영 진단에
  `request_id`와 안전한 테스트가 필요합니다.
- 새 tenant table마다 policy와 제한 role 통합 테스트를 추가해야 합니다.
- 준비·활성화의 두 migration과 두 번의 배포 검증이 필요합니다.

## Alternatives Considered

### Application Repository 조건만 유지

현재 기준선은 유지하지만 defense-in-depth가 없어 query 조건 누락을 DB가 차단하지
못하므로 선택하지 않습니다.

### Runtime을 table owner 또는 `BYPASSRLS` role로 실행

RLS를 항상 우회해 실제 보호 효과와 테스트 의미가 없어지므로 금지합니다.

### session 범위 `SET app.company_id` 사용

Connection pool에서 다음 요청에 tenant 값이 남을 수 있으므로 선택하지 않습니다.
Transaction-local `set_config(..., true)`만 사용합니다.

### Bootstrap role에 auth·link table 전체 SELECT 허용

구현은 단순하지만 tenant 확정 전 권한이 너무 넓고 query 실수로 여러 tenant의 인증
정보를 읽을 수 있으므로 선택하지 않습니다. 최소 `SECURITY DEFINER` 함수만
허용합니다.

### 첫 도입부터 `FORCE ROW LEVEL SECURITY` 적용

Runtime은 owner가 아니므로 `ENABLE`만으로 정책 적용 대상입니다. `FORCE`는
migration owner와 bootstrap 함수의 동작을 복잡하게 하므로 첫 도입에서는
선택하지 않습니다.

### PostgreSQL RLS SQL을 공통 H2 migration에 포함

H2 호환을 위해 SQL을 약화하거나 최신 migration을 `flyway.target`으로 건너뛰게
되므로 선택하지 않습니다. 공통과 PostgreSQL 전용 location을 분리합니다.

### 준비 없이 한 migration에서 policy 생성과 RLS 활성화

호환 코드·role·bootstrap 검증 전에 Login·Refresh·Worker Link가 모두 중단될 수
있으므로 선택하지 않습니다. 준비와 활성화를 별도 forward migration으로 나눕니다.

### 모든 policy에서 다른 table을 subquery

직접 `company_id` 비교보다 느리고 동시성 snapshot에 따른 보안 판단이 복잡해질 수
있으므로 기본안으로 선택하지 않습니다.

## 검토 후 확정할 항목

이 ADR을 `Accepted`로 변경하기 전에 다음을 팀 review에서 확정합니다.

- [x] 최소 bootstrap `SECURITY DEFINER` 함수 방식을 승인합니다.
- [x] 준비 migration과 활성화 migration을 분리하는 순서를 승인합니다.
- [x] 공통/PostgreSQL 전용 Flyway location 이름을 확정합니다.
- [x] RLS 적용 대상 table과 tenant 판정 경로를 실제 schema 기준으로 확정하고 검증합니다.
- [x] #9에서 role provisioning과 Secret 주입 책임을 수용합니다.
- [x] 첫 도입에서 `FORCE ROW LEVEL SECURITY`를 보류하는 결정을 확인합니다.
- [x] 장애 시 RLS 비활성화 권한·담당자·Smoke 절차를 runbook에 지정합니다.

## References

- [Issue #34: PostgreSQL RLS 기반 사업장 2차 격리](https://github.com/fowoco/server/issues/34)
- [PostgreSQL Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [PostgreSQL SET](https://www.postgresql.org/docs/current/sql-set.html)
- [PostgreSQL CREATE FUNCTION: SECURITY DEFINER](https://www.postgresql.org/docs/current/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY)
- [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Flyway Locations Setting](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-locations-setting)
- [Server Wiki: 보안과 개인정보](https://github.com/fowoco/server/wiki/05-Security-and-Privacy)
- [Server Wiki: 로컬 개발 가이드](https://github.com/fowoco/server/wiki/06-Local-Development)
- [Server Wiki: 데모 배포 가이드](https://github.com/fowoco/server/wiki/07-Demo-Deployment)
- [Server Wiki: GitHub 협업 가이드](https://github.com/fowoco/server/wiki/08-GitHub-Workflow)
