# Worker Import 구현·연동 가이드

## 한 줄 설명

HR이 CSV/XLSX 근로자 명단을 올리면 서버가 즉시 등록하지 않고, 열 연결과 행 검증을 거친 뒤 HR이 선택한 정상 행만 `worker`로 등록합니다.

```text
UPLOADED → MAPPED → REVIEW_REQUIRED 또는 READY → COMMITTED
```

오류 행을 수정하거나 제외하면 `MAPPED`로 돌아가며, `retry`로 다시 검증합니다. 일부 정상 행만 먼저 등록한 경우 남은 오류·정상 행을 계속 처리할 수 있습니다.

## 화면 단계와 API

| 화면 단계 | API | 서버 동작 |
| --- | --- | --- |
| 파일 업로드 | `POST /api/v1/imports` | CSV/XLSX 구조·크기·수식·차단 열을 검사하고 작업 생성 |
| 진행·검토 조회 | `GET /api/v1/imports/{importId}` | 현재 상태, 건수와 행별 오류 조회 |
| 열 연결 | `PUT /api/v1/imports/{importId}/mappings` | 업로드 열을 Worker 필드에 연결 |
| 자료 검증 | `POST /api/v1/imports/{importId}/validate` | 필수값·날짜·국적 코드·중복 후보 검사 |
| 오류 수정·제외 | `PATCH /api/v1/imports/{importId}/rows` | 원본과 별도로 수정값 저장 또는 행 제외 |
| 등록 확정 | `POST /api/v1/imports/{importId}/commit` | 선택한 `VALID` 행만 근로자로 등록 |
| 실패 행 재검증 | `POST /api/v1/imports/{importId}/retry` | 수정 후 아직 등록하지 않은 행 재검증 |

모든 API는 `ADMIN` 또는 `HR`만 사용할 수 있습니다. `company_id`는 요청에서 받지 않고 JWT의 `ActorContext`로 결정하며, 다른 사업장 작업은 `404`로 숨깁니다.

## 지원 필드

열 연결의 값은 아래 canonical key만 허용합니다.

- `display_name` — 필수
- `nationality_code` — ISO alpha-2, 예: `VN`
- `preferred_language`
- `visa_type`
- `stay_expiry_date`
- `contract_start_date`
- `contract_end_date`
- `employment_permit_end_date`
- `employment_activity_end_date`

날짜는 `YYYY-MM-DD` 형식입니다. 새 근로자의 `work_status`는 기존 Worker 등록 규칙과 동일하게 `ACTIVE`로 시작합니다.

## 요청 예시

### 1. 업로드

```http
POST /api/v1/imports
Authorization: Bearer <access-token>
Idempotency-Key: import-20260807-001
Content-Type: multipart/form-data

file=@workers.csv
```

### 2. 열 연결

```json
{
  "expected_version": 0,
  "mappings": {
    "이름": "display_name",
    "국적": "nationality_code",
    "언어": "preferred_language",
    "체류만료일": "stay_expiry_date"
  }
}
```

### 3. 오류 행 수정

```json
{
  "expected_version": 2,
  "rows": [
    {
      "row_number": 3,
      "excluded": false,
      "values": {
        "stay_expiry_date": "2027-02-01"
      }
    }
  ]
}
```

### 4. 정상 행 등록

```http
POST /api/v1/imports/{importId}/commit
Idempotency-Key: commit-20260807-001
```

```json
{
  "expected_version": 4,
  "selected_row_numbers": [2, 3]
}
```

`selected_row_numbers`를 생략하면 현재 `VALID`인 모든 행을 등록합니다. 등록에 사용한 모든 `Idempotency-Key`와 요청·응답 snapshot을 별도 기록하므로 부분 등록을 여러 번 수행한 뒤 이전 요청을 다시 보내도 당시 성공 응답을 반환하며, 같은 키로 다른 행을 요청하면 `409`로 거부합니다.

## 파일·개인정보 규칙

- 최대 5MB, 1,000개 데이터 행, 50개 열까지만 허용합니다.
- UTF-8 CSV와 매크로가 없는 XLSX만 지원합니다.
- XLSX 날짜 셀은 화면 표시 형식과 관계없이 `YYYY-MM-DD`로 정규화합니다.
- CSV 수식 시작 문자(`=`, `+`, `-`, `@`)와 XLSX Formula Cell을 거부합니다.
- 여권번호·외국인등록번호·계좌번호와 MVP에서 수집하지 않는 연락처·이메일·주소·생년월일·법정실명 열은 업로드 단계에서 거부합니다.
- 원본 행 전체를 일반 로그나 감사로그에 남기지 않습니다.
- 원본 파일 만료 예정 시각은 `source_file_expires_at`으로 계산하며 기본값은 7일입니다.

`WORKER_IMPORT_SOURCE_RETENTION`은 보존기간 계산값입니다. 실제 Object Storage 삭제 배치는 File Storage 운영 정책과 함께 연결해야 하며, 만료 시각이 지나도 DB 이력을 임의 삭제하지 않습니다.

## 오류를 읽는 방법

행 오류는 `field`, `code`, `message`로 반환됩니다.

```json
{
  "field": "stay_expiry_date",
  "code": "INVALID_DATE",
  "message": "날짜는 YYYY-MM-DD 형식이어야 합니다."
}
```

대표 코드는 `REQUIRED`, `INVALID_FORMAT`, `INVALID_DATE`, `DATE_ORDER`, `DUPLICATE_CANDIDATE`입니다. 서버는 중복 후보를 자동 병합하지 않으며 HR이 값을 수정하거나 해당 행을 제외해야 합니다.

## 개발자가 확인할 테스트

```bash
./gradlew test --tests '*DefaultWorkerImportFileParserTest' \
  --tests '*WorkerImportApiIntegrationTest'
```

PostgreSQL 환경변수가 준비된 경우 Migration과 RLS도 확인합니다.

```bash
POSTGRES_TEST_ENABLED=true ./gradlew test \
  --tests '*PostgreSqlMigrationTests' \
  --tests '*PostgreSqlWorkerImportRlsTest'
```
