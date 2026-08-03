# Demo Seed 운영 시나리오

Demo Seed는 현재 구현된 API를 로컬에서 현실적인 운영 데이터로 확인하기 위한
개발 전용 데이터셋이다. Figma의 업무·근로자 구성을 참고하되, 서버에 존재하는
도메인과 상태만 사용한다.

> Demo Seed는 로컬 H2 또는 개인 PostgreSQL 개발 DB 전용이다. 공유 `dev`와
> `prod`에서 활성화하거나 실제 개인정보·Secret을 섞어 사용하면 안 된다.

## 실행 조건

Demo Seed의 기본값은 `false`다. 활성화할 때는 12자 이상의 로컬 전용 비밀번호를
실행 환경의 Secret으로 제공해야 한다. 모든 데모 계정은 이 비밀번호를 공유하며,
저장할 때는 BCrypt 해시만 남긴다.

```bash
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='로컬 전용 12자 이상 값'
./gradlew bootRun
```

팀 공통 PostgreSQL 실행 스크립트는 `.env.local`을 읽고 `dev` profile과 Demo
Seed를 함께 활성화한다.

```bash
# macOS / Linux
cp .env.example .env.local
./scripts/run-dev.sh
```

```powershell
# Windows PowerShell
Copy-Item .env.example .env.local
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-dev.ps1
```

`.env.local`에는 로컬 PostgreSQL 자격 증명, JWT Secret과 Demo 비밀번호를
직접 설정한다. 이 파일과 실제 값은 Git, Issue, 로그 또는 메신저에 올리지 않는다.

## 대표 로그인 계정

| 회사 | 역할 | 이메일 | 용도 |
| --- | --- | --- | --- |
| Demo Company | `ADMIN` | `demo.admin@example.com` | 기본 관리자, 환경변수로 변경 가능 |
| Demo Company | `ADMIN` | `demo.ops@example.com` | 운영 관리자 |
| Demo Company | `HR` | `demo.hr01@example.com` | 일반 업무 조회·처리 |
| Demo Company | `VIEWER` | `demo.viewer01@example.com` | 읽기 전용 권한 확인 |
| Test Company | `ADMIN` | `test.admin@example.com` | 테넌트 격리 확인 |
| Test Company | `HR` | `test.hr@example.com` | 작은 테스트 회사 업무 확인 |
| Test Company | `VIEWER` | `test.viewer@example.com` | 작은 테스트 회사 읽기 권한 확인 |

`DEMO_SEED_ADMIN_EMAIL`을 변경하면 첫 번째 Demo Company 관리자 이메일만
변경된다. Test Company는 Demo Company와 분리된 작은 격리 검증용 데이터셋이다.

## 최종 데이터 수량

### FOWOCO Demo Company

| 데이터 | 수량 | 주요 분포 |
| --- | ---: | --- |
| 계정 | 20 | `ADMIN` 2, `HR` 12, `VIEWER` 6 |
| 근로자 | 28 | `ACTIVE`와 `ON_LEAVE`, 다양한 체류 만료 구간 |
| 업무 | 24 | 세 가지 지원 업무 유형과 여덟 가지 상태 |
| 근로자 서류 | 84 | `VERIFIED` 48, `SUBMITTED` 20, `MISSING` 16 |
| 체크리스트 항목 | 68 | 24개 업무에 연결 |
| 승인 요청 | 13 | `PENDING` 4, `APPROVED` 7, `REJECTED` 1, `INVALIDATED` 1 |
| 상태 전이 이력 | 52 | 초안부터 완료·취소까지의 업무별 이력 |
| 외부 제출 | 6 | 고용센터 또는 출입국·외국인청 제출 시나리오 |
| 완료 증빙 | 10 | 문서, 접수증, 공식 결과, HR 확인 |
| 문서 요청 초안 | 5 | 네팔어, 베트남어, 인도네시아어, 미얀마어 |
| Audit Event | 96 | `HR_USER` 83, `AI_AGENT` 3, `SYSTEM_RULE` 6, `WORKER_LINK` 4 |
| StoredFile | 0 | 실제 파일이나 가짜 저장 경로를 만들지 않음 |

Demo Company 업무 유형은 `STAY_PERIOD_EXTENSION` 10개, `RECONTRACT` 8개,
`EMPLOYMENT_PERIOD_EXTENSION` 6개다.

업무 상태는 `DRAFT` 3개, `NEEDS_INFO` 2개, `READY_FOR_REVIEW` 4개,
`APPROVED` 2개, `WAITING_WORKER` 4개, `WAITING_EXTERNAL` 3개,
`COMPLETED` 5개, `CANCELLED` 1개다.

서류 유형은 `PASSPORT_COPY` 26개, `ARC` 28개, `CONTRACT` 22개,
`PERMIT` 8개다. 만료일은 이미 만료, 30일 이내, 31~90일, 90일 초과,
미상 구간을 모두 포함한다.

### FOWOCO Test Company

| 데이터 | 수량 |
| --- | ---: |
| 계정 | 3 |
| 근로자 | 5 |
| 업무 | 3 |
| 근로자 서류 | 8 |
| Audit Event | 8 |

Test Company에는 Demo Company의 전체 운영 데이터를 복제하지 않는다. Demo
계정으로 Test Company 업무를 조회하거나 그 반대로 조회하면 `404` 또는 빈
목록이 반환되어야 한다.

## 대표 근로자 시나리오

모든 날짜는 주입된 `Clock`을 기준으로 상대 계산된다. `D+12`와 같은 값은 서버
실행일이 바뀌면 함께 이동한다.

| 근로자 | 대표 상황 | 연결 데이터 |
| --- | --- | --- |
| 응웬반A | 체류 만료 `D+12`, 재계약 검토 | 같은 `caseId`의 `READY_FOR_REVIEW`, `DRAFT`, `WAITING_WORKER` 업무, 승인 요청, 체크리스트, 문서 요청 초안, Audit Event |
| 소팔 타망 | 고용기간 연장 `D+4`, 정보 보완 필요 | `NEEDS_INFO`, 미완료 체크리스트, 누락·제출 서류 |
| 라니 위자야 | 체류 서류 응답 대기 | `WAITING_WORKER`, 다국어 문서 요청 초안, 요청 관련 Audit Event |
| 파티마 누르 | 외국인등록증 사본 대기 | `WAITING_WORKER`, ARC 요청 초안과 서류 상태 |
| 민 아웅 | 재계약·서류 준비 `D+20` | AI 후보 `DRAFT`, 계약서·허가서 요청 초안 |
| 아디 수르야 | 고용기간 연장 자료 보완 | 지원되는 업무 유형의 `NEEDS_INFO`; 신규 등록 업무는 만들지 않음 |
| 모하메드 라힘 | 오늘 마감된 체류기간 연장 완료 | `COMPLETED`, 승인 상태, 외부 제출, 완료 증빙, 상태 전이 이력 |

응웬반A의 세 업무는 별도 Case 엔티티 없이 같은 `caseId`로 연속성만 표현한다.
서버는 Case 진행률이나 Case 전용 API를 제공하지 않는다.

## 클라이언트에서 확인되는 데이터

현재 클라이언트는 서버 원본 값을 조합해 화면용 문구와 분류를 만든다. Demo
Seed에는 한국어 배지 문자열이나 별도 UI 전용 필드를 저장하지 않는다.

### 업무 목록과 Metric Strip

| 화면 의미 | 서버 데이터 | 기대 수량·예시 |
| --- | --- | --- |
| 승인 대기 | `READY_FOR_REVIEW` | 4개 |
| AI 준비 완료 | `DRAFT` | 3개 |
| 긴급 업무 | `dueDate <= today + 7일` | 오늘, `D+3`, `D+4`, `D+6`, `D+7` 등 복수 |
| 오늘 완료 | `COMPLETED`이고 `updatedAt`이 오늘 | 5개 |

근로자 분류에는 다음 세 범주가 모두 나타난다.

- `needs-review`: `READY_FOR_REVIEW`, `WAITING_WORKER`, `WAITING_EXTERNAL` 중 하나 이상
- `ai-suggested`: 위 상태가 없고 `DRAFT` 또는 `NEEDS_INFO`가 존재
- `done`: 위 두 조건에 해당하지 않음

### 문서 목록

- 검토 필요: `SUBMITTED`
- 만료 예정: 오늘부터 30일 이내의 `expiryDate`
- 누락 문서: `MISSING`
- 요청 중: 현재 클라이언트가 `MISSING`으로 근사
- 최근 업로드: 현재 클라이언트가 `SUBMITTED`로 근사

`요청 중`과 `최근 업로드`는 현재 서버가 별도 수명주기를 제공하지 않기 때문에
정확한 상태가 아니다.

### 업무 상세와 Audit

- 업무 상세 API에서 연결된 체크리스트를 확인할 수 있다.
- `94000000-0000-0000-0000-000000000002` 업무는 기존 호환성을 위해
  `TASK_CREATED`, `TASK_UPDATED`, `APPROVAL_REQUESTED` 활동 3개를 유지한다.
- 확장 Audit에는 업무 생성, 체크리스트 변경, 승인 결과, 외부 제출, 완료 증빙,
  문서 요청 초안, 완료·취소 이벤트가 포함된다.
- `WORKER_LINK` 이벤트는 근로자가 제공한 문서 정보가 업무에 반영된 맥락만
  표현하며 실제 링크 토큰이나 전송·열람 상태를 만들지 않는다.

## Figma 표현 범위

### 현재 모델로 직접 표현한 항목

- 근로자별 지원 업무, 상태, 마감일과 체류 만료일
- 업무 체크리스트와 승인 요청·결과
- 외부 제출, 완료 증빙과 상태 전이 이력
- 문서 누락·제출·검증 및 만료 구간
- 다국어 문서 요청 초안
- HR 사용자, AI 에이전트, 시스템 규칙, 근로자 링크 맥락의 Audit Event

### 현재 모델로 근사한 항목

- 문서 요청·응답 대기: `WAITING_WORKER`, `MISSING`, 문서 요청 초안과 Audit로 표현
- 요청 중: 클라이언트가 `MISSING`으로 근사
- 최근 업로드: 클라이언트가 `SUBMITTED`로 근사
- Case 연속성: 별도 엔티티 없이 여러 업무의 동일한 `caseId`로 표현

### 의도적으로 제외한 항목

- 담당자 미지정 업무
- `WORKER_ONBOARDING`, `EMPLOYMENT_CHANGE` 업무 유형
- 실제 Worker Secure Link와 등록·전송·응답·읽음·안읽음 수명주기
- 메시지, 근로자 질문, Ticket
- Case 엔티티, Case 진행률과 Case API
- Dashboard 집계 API; 현재 Dashboard는 클라이언트 정적 데이터 유지
- 급여, 근태, 일정, 수입, OCR, AI 분석 모델
- 실제 파일 바이너리, StoredFile 또는 가짜 파일 경로
- 실제 여권번호, 외국인등록번호, 전화번호, 주소, 임금, 토큰, 자격 증명

## 멱등성, 충돌과 날짜 갱신

모든 예약 레코드는 고정 UUID를 사용한다. 같은 설정으로 다시 실행하면 중복을
만들지 않고 기존 레코드의 회사·외래키 소유권과 핵심 값을 검증한다. 예약 ID나
이메일이 다른 데이터에 사용된 경우 기존 데이터를 덮어쓰지 않고 시작을
중단한다.

근로자 체류 만료일, 계약 종료일, 업무 마감일처럼 실행일 기준으로 설계된 값은
재실행 시 현재 `Clock`에 맞게 이동한다. 고정된 식별자와 시나리오 의미는
유지된다.

## 초기화와 재실행

단순 재실행은 DB를 초기화할 필요가 없다. 서버를 종료한 뒤 같은 설정으로 실행하면
멱등성 검증 후 기존 시드를 재사용한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-dev.ps1
```

완전히 새 DB로 확인하려면 먼저 서버를 종료하고, **개인 로컬 DB가
`fowoco_test`인지 다시 확인한 뒤에만** 삭제·재생성한다. 다음 명령은 해당 DB의
모든 데이터를 복구 불가능하게 삭제한다.

```bash
dropdb -h localhost -p 5432 -U postgres fowoco_test
createdb -h localhost -p 5432 -U postgres fowoco_test
```

Docker를 사용한다면 개인 로컬 PostgreSQL 컨테이너의 `fowoco_test` DB 또는 전용
볼륨만 재생성한다. 공유 컨테이너나 다른 프로젝트 볼륨은 삭제하지 않는다. 그 후
실행 스크립트를 다시 호출하면 Flyway 적용 후 Demo Seed가 생성된다.

H2 `local`은 인메모리 DB이므로 애플리케이션을 종료하고 다시 실행하면 초기화된다.

## 확인 쿼리

```sql
SELECT company_id, COUNT(*) FROM worker GROUP BY company_id;
SELECT company_id, COUNT(*) FROM task GROUP BY company_id;
SELECT company_id, COUNT(*) FROM worker_document GROUP BY company_id;
SELECT company_id, COUNT(*) FROM audit_event GROUP BY company_id;
```

실행 로그의 운영 데이터 요약은 다음 값을 포함한다.

```text
demo_task_count=24 demo_document_count=84 demo_audit_count=96
test_task_count=3 test_document_count=8 test_audit_count=8
```
