# PostgreSQL Runtime Connection Timeout 운영 가이드

FOWOCO Server가 생성하는 PostgreSQL Runtime Hikari Connection에는 장시간 SQL과
lock 대기가 Connection Pool 전체 장애로 확산되지 않도록 Session 범위 안전선을
적용합니다.

## 설정

| 환경변수 | 기본값 | 의미 |
| --- | ---: | --- |
| `DB_STATEMENT_TIMEOUT` | `30s` | 개별 SQL의 최대 실행시간 |
| `DB_LOCK_TIMEOUT` | `3s` | row/table lock 획득 최대 대기시간 |

Spring Boot `Duration` 형식을 사용합니다. `30s`, `3000ms`, `PT30S`, `1000us`를
사용할 수 있으며 접미사가 없는 정수는 millisecond입니다. 운영에서는 혼동을
막기 위해 단위를 명시합니다.

두 값은 다음 조건을 모두 만족해야 합니다.

- 0보다 크고 정확한 정수 millisecond로 표현 가능
- `2147483647ms` 이하
- `DB_LOCK_TIMEOUT < DB_STATEMENT_TIMEOUT`

`1000us`는 정확히 `1ms`이므로 허용하지만 `1500us`는 millisecond 미만 remainder가
있어 시작 단계에서 거부합니다. `0`으로 guard를 비활성화할 수 없습니다.

## 지원하는 DataSource 구성

PostgreSQL mode에서는 `spring.datasource.url` 기반의 직접적인
`jdbc:postgresql:` URL과 HikariCP만 지원합니다. URL, username, password와 선택적인
driver는 `spring.datasource.*`를 단일 설정 원본으로 사용합니다.

다음 구성은 중복 설정 또는 물리 Connection 초기화 보장 상실을 막기 위해 시작
단계에서 거부합니다.

- JNDI DataSource
- Hikari namespace의 JDBC URL, username, password, driver 중복 설정
- Hikari `data-source-class-name`, `data-source-j-n-d-i`, `connection-init-sql`
- Hikari 이외의 명시적인 `spring.datasource.type`
- p6spy 등 wrapper/proxy JDBC URL
- `spring.datasource.connection-fetch=lazy`

향후 DataSource proxy나 decorator를 도입하면 물리 Connection 생성 시점의 Session
초기화가 유지되는지 다시 검토해야 합니다.

## 적용 경계

Runtime Pool은 새 물리 Connection을 등록하기 전에 다음과 같은 하나의 초기화 SQL을
실행합니다.

```sql
SELECT
    pg_catalog.set_config('statement_timeout', '30000ms', false),
    pg_catalog.set_config('lock_timeout', '3000ms', false)
```

환경변수 원문은 SQL에 들어가지 않으며 검증된 정수 millisecond만 사용합니다.
초기화 SQL이 실패한 Connection은 정상 Pool Connection으로 제공되지 않습니다.

- PostgreSQL Runtime Hikari Connection에만 적용
- Flyway 전용 Connection에는 미적용
- local/test H2에는 미적용
- 새 물리 Connection마다 한 번 실행
- transaction 또는 checkout마다 반복 실행하지 않음

## Session 변경 제한

HikariCP는 Connection 반환 시 PostgreSQL의 모든 임의 Session parameter를 reset하지
않습니다. Runtime production 코드에서 `SET statement_timeout`,
`SET lock_timeout` 또는 Session 범위 `set_config(..., false)`를 사용하면 다음
요청에 변경값이 남을 수 있으므로 금지합니다.

업무별 예외가 필요하면 별도 Issue에서 Spring Transaction 내부의 `SET LOCAL`만
사용합니다. `SET LOCAL`은 commit 또는 rollback 후 Session 기본값으로 돌아가야
합니다. 이번 구현에는 checkout interceptor나 Session reset proxy가 없습니다.

## Timeout 분류와 HTTP 계약

PostgreSQL의 SQLState만으로 원인을 확정하지 않습니다.

- `57014`와 canonical statement-timeout diagnostic이 함께 있으면 confirmed
  statement timeout
- `55P03`과 canonical lock-timeout diagnostic이 함께 있으면 confirmed lock timeout
- SQLState만 일치하면 ambiguous cancellation 또는 lock failure
- 권한·RLS `42501`, bootstrap 인자 `22023`은 timeout이 아님

Confirmed timeout은 안전한 공개 오류 `503 SERVICE_TEMPORARILY_UNAVAILABLE`로
응답합니다. `Retry-After`는 제공하지 않습니다. 복구 시점을 신뢰할 수 없기
때문입니다. Ambiguous cancellation은 기존 `500 INTERNAL_SERVER_ERROR`를 유지합니다.

PostgreSQL `lc_messages`가 영어가 아니면 canonical diagnostic을 확인하지 못해 실제
timeout도 ambiguous로 처리될 수 있습니다. False positive를 막기 위한 보수적
정책이며 confirmed metric이 과소 집계될 수 있습니다.

Client와 Gateway는 모든 503을 자동 재시도하지 않습니다. GET처럼 본질적으로
idempotent한 요청만 exponential backoff와 jitter로 제한적으로 재시도할 수 있습니다.
POST·PATCH 등 변경 요청은 `Idempotency-Key`로 동일 요청이 보장될 때만 재시도하며,
그렇지 않으면 자동 재시도하지 않습니다.

## 로그와 Metric

Confirmed 및 ambiguous 분류 로그에는 `requestId`, HTTP method, 안전한 route pattern,
classification, SQLState, exception type만 기록합니다. Raw URI, SQL, bind parameter,
PostgreSQL diagnostic message, credential, 개인정보와 stack trace는 기록하지 않습니다.
상세 원인은 request ID와 발생 시각을 기준으로 PostgreSQL 서버 로그에서 조사합니다.

Confirmed HTTP timeout은 다음 low-cardinality counter에 집계합니다.

```text
fowoco.database.timeouts{type="statement"}
fowoco.database.timeouts{type="lock"}
```

Ambiguous cancellation과 Background Outbox failure는 이 metric 범위가 아닙니다.
DB Transaction과 metric 증가는 원자적이지 않으므로 사건당 exactly-once 집계를
보장하지 않습니다. Outbox의 retry, backoff, `maxAttempts`와 기존 metric 정책은
변경하지 않습니다.

## 설정 변경과 관측

```text
환경변수 변경
→ 애플리케이션 전체 재시작
→ 새 Runtime Pool 생성
→ current_setting 확인
→ metric과 정상 Query 관측
```

기존 물리 Connection에는 환경변수 변경이 즉시 반영되지 않습니다. Staging에서는
confirmed timeout 횟수, 정상 Query 실행시간, Pool 사용량과 Outbox 지연을 함께
확인합니다.

정상 workload가 반복해서 timeout되면 다음 순서로 대응합니다.

1. Query와 index 개선
2. Transaction 범위 축소
3. workload 분리
4. 후속 Issue에서 `SET LOCAL` override 검토
5. 승인 후 전역 기본값 조정

전역값 상향을 첫 대응으로 사용하지 않습니다.

## Rollback

문제가 발생하면 이전 애플리케이션 버전을 재배포하고 Runtime Pool 전체를
재생성한 뒤 Session 적용값을 확인합니다. `ALTER ROLE`, `ALTER DATABASE` 또는
timeout `0` 설정으로 우회하지 않습니다.

실제 배포 환경변수, Runtime/Flyway credential과 Secret 연결은 Issue #9의 범위입니다.
