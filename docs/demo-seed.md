# Demo Seed 운영 시나리오

Demo Seed는 로컬 H2 또는 개인 PostgreSQL 개발 DB에서 제품 흐름과 Showcase 화면을
재현하기 위한 합성 데이터다. 실제 개인정보, 행정 문서 원본, 운영 Secret은 포함하지
않는다.

Demo Seed에는 목적이 다른 두 종류의 데이터가 함께 있다.

- **Golden Flow 시작 데이터**: HR이 자연어 요청을 입력하는 순간부터 실제 흐름을
  시연하기 위한 최소 선행 데이터
- **Showcase Seed**: 목록·업무함·문서함·대시보드의 다양한 상태와 화면 밀도를 위한
  다른 근로자의 예시 데이터

예약 ID와 Figma 대응 관계는
[Demo Fixture Manifest](demo-seed-fixture-manifest.md)에서 확인한다.

> Demo Seed의 기본값은 `false`다. 공유 `dev`나 `prod`에서 활성화하지 않으며, 개인
> 개발 DB에서도 합성 비밀번호만 사용한다.

## 실행 방법

Demo Seed를 활성화하려면 12자 이상의 로컬 전용 비밀번호가 필요하다.

```bash
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='로컬 전용 12자 이상 값'
./gradlew bootRun
```

```powershell
$env:DEMO_SEED_ENABLED = "true"
$env:DEMO_SEED_ADMIN_PASSWORD = "로컬 전용 12자 이상 값"
.\gradlew.bat bootRun
```

기본 `local` profile은 H2 인메모리 DB를 사용한다. PostgreSQL `dev` profile은
`.env.local`을 구성한 뒤 저장소 스크립트로 실행할 수 있다.

```bash
cp .env.example .env.local
./scripts/run-dev.sh
```

```powershell
Copy-Item .env.example .env.local
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-dev.ps1
```

PostgreSQL 17 Compose 실행은 [배포 Runbook](deployment-runbook.md)의 로컬 통합 실행
절차를 따른다.

## 로그인 계정

모든 계정은 `DEMO_SEED_ADMIN_PASSWORD`에 지정한 동일한 합성 비밀번호를 사용하고,
DB에는 BCrypt hash만 저장한다.

| 회사 | 역할 | 이메일 | 용도 |
| --- | --- | --- | --- |
| Demo Company | `ADMIN` | `demo.admin@example.com` | 기본 관리자 |
| Demo Company | `ADMIN` | `demo.ops@example.com` | 운영 관리자 |
| Demo Company | `HR` | `demo.hr01@example.com` | Golden Flow 대표 HR |
| Demo Company | `VIEWER` | `demo.viewer01@example.com` | 읽기 권한 확인 |
| Test Company | `ADMIN` | `test.admin@example.com` | tenant 격리 확인 |
| Test Company | `HR` | `test.hr@example.com` | tenant 격리 확인 |
| Test Company | `VIEWER` | `test.viewer@example.com` | tenant 격리 확인 |

Demo Company에는 `ADMIN` 2명, `HR` 12명, `VIEWER` 6명이 있고 Test Company에는
역할별 한 명씩 있다. 두 회사의 데이터는 `company_id`로 분리된다.

## Golden Flow 시작 상태

대표 시작 Worker는 다음과 같다.

| 항목 | 값 |
| --- | --- |
| Worker ID | `92000000-0000-0000-0000-000000000006` |
| 이름 | 응웬반A |
| Company ID | `90000000-0000-0000-0000-000000000001` |
| 국적·기본 언어 | `VN` · `vi` |
| 근로 상태 | `ACTIVE` |
| 현재 상대 날짜 | 체류 만료 `D+45`, 계약 시작 `D-1년`, 계약 종료 `D+180` |
| 여권 사본 | `PASSPORT_COPY`, `VERIFIED`, 만료 `D+365` |
| 외국인등록증 사본 | `ARC`, `MISSING`, 만료일 없음 |
| Workflow Catalog | classpath projection, version `0.2.0` |

상대 날짜는 Worker가 처음 생성되는 날을 기준으로 저장되며 재실행 시 기존 값을
바꾸지 않는다. 현재 Worker 날짜 필드는 정확한 E-9 취업활동기간 의미를 모두 표현하지
않는다. `D+45`는 대표 요청을 시작할 수 있는 데모 신호일 뿐이며, 정확한 E-9 날짜 의미와
3년 만료 판정은 Issue #84의 후속 범위다.

HR은 같은 Demo Company 범위에서 응웬반A를 조회하고 현재 구현된 AI 요청·Candidate
결정·Case/Task·승인·Worker Link·제출·증빙 흐름을 진행할 수 있다. 별도 Workplace,
Worker 연락처, 활성 Agent Version 또는 활성 Prompt Version Seed 모델은 만들지 않는다.
Agent·Prompt 버전은 실제 AI 실행 후 `AiAttempt` 메타데이터로 기록된다.

### 시연 전에 존재하는 데이터

- Demo Company와 HR 사용자·현재 역할
- 응웬반A Worker 기본정보
- 응웬반A의 검증된 유효 여권 사본 상태와 외국인등록증 사본 누락 상태
- 현재 구현된 Workflow Catalog와 Workflow Version
- 현재 구현된 Task Type, 공통 코드와 상태값

응웬반A의 두 `WorkerDocument`는 Golden Flow 판단에 필요한 최소 메타데이터다. 여권은
`VERIFIED`이며 만료일이 현재보다 미래이고, ARC는 필요한 문서지만 현재 누락된 상태를
명시하는 `MISSING`이다. 두 문서 모두 `task_id`와 `file_id`가 없으며 여권번호,
외국인등록번호, OCR 결과와 신분증 이미지를 포함하지 않는다.

AI Runtime이 해당 필드를 요청하면 Server는 같은 `company_id` 범위에서 문서를 조회해
`passport_copy_status`, `passport_copy_expiry_date`, `arc_status`, `arc_expiry_date`를
구조화된 Context로 제공한다. Runtime은 이 Server 소유 값을 다른 값으로 변경할 수 없다.

### 시연 전에 존재하지 않는 데이터

- 응웬반A 요청의 `AiRun`, `AiAttempt`, Question, Candidate, Candidate Decision
- 응웬반A의 대표 Case와 Task
- 해당 Task의 Checklist, Approval, Document Request Draft
- 응웬반A의 계약서 `WorkerDocument`, 업로드 파일, OCR 결과
- `WorkerLink`, `WorkerResponse`
- `ExternalSubmission`, `Evidence`
- 대표 흐름의 Activity, Audit Event와 완료 상태

따라서 시연은 다음 요청을 입력하는 시점부터 시작한다.

> 응웬반A가 3년 만료 예정이야. 재계약하고 체류연장 준비해줘.

현재 모델에서 판단 근거가 부족하면 실제 AI 흐름이 추가 정보를 질문해야 하며, Seed가
완료 결과를 대신 만들지 않는다.

## 현재 Seed 수량

### FOWOCO Demo Company

| 데이터 | 수량 | 주요 분포 |
| --- | ---: | --- |
| 계정 | 20 | `ADMIN` 2, `HR` 12, `VIEWER` 6 |
| 근로자 | 28 | `ACTIVE` 25, `ON_LEAVE` 3 |
| Case | 21 | Showcase Task별 Case |
| Task | 21 | 체류연장 9, 재계약 7, 고용기간 연장 5 |
| 근로자 문서 | 83 | 여권 26, ARC 28, 계약서 21, 허가서 8 |
| 체크리스트 항목 | 60 | Showcase Task에 연결 |
| 승인 요청 | 12 | `PENDING` 3, `APPROVED` 7, `REJECTED` 1, `INVALIDATED` 1 |
| 상태 전이 이력 | 48 | Showcase Task 상태 이력 |
| 외부 제출 | 6 | 합성 제출처와 안전한 참조 번호 |
| 완료 증빙 | 10 | 문서·접수증·공식 결과·HR 확인 |
| 문서 요청 초안 | 4 | 다른 근로자의 Showcase 초안 |
| Audit Event | 88 | HR 77, AI 2, 시스템 6, Worker Link 3 |
| StoredFile | 3 | 합성 계약서·접수증·결과 PDF |

Task 상태는 `DRAFT` 2, `NEEDS_INFO` 2, `READY_FOR_REVIEW` 3,
`APPROVED` 2, `WAITING_WORKER` 3, `WAITING_EXTERNAL` 3,
`COMPLETED` 5, `CANCELLED` 1이다.

문서 상태는 `VERIFIED` 47, `SUBMITTED` 20, `MISSING` 16이다. 응웬반A의 여권과
ARC 문서 2건이 이 83건에 포함된다.

### FOWOCO Test Company

| 데이터 | 수량 |
| --- | ---: |
| 계정 | 3 |
| 근로자 | 5 |
| Case | 3 |
| Task | 3 |
| 근로자 문서 | 8 |
| Audit Event | 8 |

Test Company는 Demo Company 전체 데이터를 복제하지 않는다. 상호 조회는 빈 목록 또는
`404`가 되어야 한다.

## Showcase Seed 보존

응웬반A와 직접 연결됐던 기존 Case 1건, Task 3건, 문서 3건과 그 하위 데이터만 현재
생성 목록에서 제외한다. 다른 27명 근로자의 기존 값·고정 ID·상태·관계는 유지한다.

Catalog는 과거 전체 fixture를 기존 순번으로 먼저 구성한 다음 Golden Flow 관련 항목만
filter한다. 이 방식은 목록 위치로 파생되는 다른 Showcase ID가 앞으로 당겨지는 것을
막는다. 회귀 테스트는 다른 Worker의 ID 집합과 관계가 기존 baseline과 같은지 검증한다.

## 멱등성, 구버전 DB와 초기화

모든 Seed record는 고정 UUID 또는 안정적인 business key를 사용한다. 같은 DB에서 다시
실행하면 현재 record를 재사용하고 소유권·핵심 값·snapshot을 검증하며, 날짜·timestamp와
JPA version을 다시 쓰지 않는다. PostgreSQL에서 서버를 종료하고 동일 DB로 재기동해도
수량과 고정 ID가 변하지 않아야 한다.

구버전 Demo Seed가 응웬반A의 제거 대상 예약 ID를 이미 저장한 DB는 자동 정리하지 않는다.
서버는 다음 메시지로 fail-fast한다.

```text
legacy Golden Flow demo seed rows detected; reset the personal demo database or volume
```

이는 시연 과정에서 사용자가 만든 Case나 Task를 Seed가 임의 삭제하지 않기 위한 정책이다.
개인 Demo DB 또는 전용 Compose volume임을 확인한 뒤 초기화하고 다시 실행한다. 공유 DB나
다른 프로젝트 volume을 삭제하면 안 된다. 이 정리를 위한 Flyway Migration은 없다.

H2 `local`은 인메모리 DB이므로 애플리케이션을 종료하고 다시 실행하면 초기화된다.

## PostgreSQL 17 호환성과 테스트

`DemoCaseSeeder`의 직접 SQL은 `Instant`를 PostgreSQL JDBC에 그대로 전달하지 않고
JDBC 경계에서 `Timestamp.from(instant)`로 변환한다. Domain의 시간 타입과 DB 스키마는
변경하지 않았고 Flyway Migration도 추가하지 않았다.

일반 테스트에서는 PostgreSQL 환경 테스트가 skip된다. 실제 PostgreSQL 17 검증은 다음
환경 변수를 같은 shell에 설정한 뒤 실행한다.

```powershell
$env:POSTGRES_TEST_ENABLED = "true"
$env:POSTGRES_TEST_URL = "jdbc:postgresql://localhost:5432/fowoco_test"
$env:POSTGRES_TEST_USERNAME = "<test-user>"
$env:POSTGRES_TEST_PASSWORD = "<test-password>"
.\gradlew.bat clean test
```

자동 테스트는 다음을 확인한다.

- PostgreSQL timestamp 저장·재조회와 `DemoCaseSeeder` 재실행
- PostgreSQL `dev` profile Application Context 전체 기동
- 빈 DB의 전체 Demo Seed 실행
- 같은 DB에서 Application Context 재기동과 전체 Seed 재실행
- 응웬반A Golden Flow 시작 상태
- 전체 수량과 Showcase Case timestamp 불변

Issue #94 검증에서는 PostgreSQL 17의 빈 Docker DB로 Demo Seed 활성 서버의 첫 기동이
성공했고, 서버를 중지한 뒤 같은 DB volume으로 재기동해도 Seed 오류 없이 정상
기동했다. 두 번째 기동에서도 응웬반A Worker가 유지됨을 확인했다. 세부 수량·미생성
상태·timestamp 불변은 위 PostgreSQL 자동 통합 테스트가 검증한다.

## 확인 쿼리

```sql
SELECT company_id, COUNT(*) FROM worker GROUP BY company_id;
SELECT company_id, COUNT(*) FROM workflow_case GROUP BY company_id;
SELECT company_id, COUNT(*) FROM task GROUP BY company_id;
SELECT company_id, COUNT(*) FROM worker_document GROUP BY company_id;
SELECT company_id, COUNT(*) FROM audit_event GROUP BY company_id;

SELECT worker_id, display_name, nationality_code, preferred_language, work_status
FROM worker
WHERE worker_id = '92000000-0000-0000-0000-000000000006';

SELECT COUNT(*) FROM workflow_case
WHERE worker_id = '92000000-0000-0000-0000-000000000006';
SELECT COUNT(*) FROM task
WHERE worker_id = '92000000-0000-0000-0000-000000000006';
SELECT COUNT(*) FROM worker_document
WHERE worker_id = '92000000-0000-0000-0000-000000000006';

SELECT document_type, submission_status, expiry_date, task_id, file_id
FROM worker_document
WHERE worker_id = '92000000-0000-0000-0000-000000000006'
ORDER BY document_type;
```

기대 로그의 핵심 수량은 다음과 같다.

```text
demo_task_count=21 demo_stored_file_count=3 demo_document_count=83 demo_audit_count=88
test_task_count=3 test_document_count=8 test_audit_count=8
```

## 범위 밖

- #84의 E-9 날짜·비자정보 필드와 정확한 3년 만료 판정
- 별도 Workplace 및 Worker 연락처 모델
- 활성 Agent/Prompt Version Registry Seed
- OCR Job·결과·추출값과 신분증 이미지
- 여권번호·외국인등록번호·주소 등 상세 개인정보
- 외부 기관 자동 로그인·자동 제출
- 실제 SMS·메신저 발송
- Demo 단계별 S0~S5 profile과 범용 BPMN/Dependency 엔진
