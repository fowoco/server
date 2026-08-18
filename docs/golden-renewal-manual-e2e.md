# 재계약·연장 Golden Flow 수동 시연 가이드

이 문서는 합성 근로자 `응웬반A`의 재계약, 취업활동기간 연장, 체류기간 연장을
HR과 근로자 역할로 직접 끝까지 진행하기 위한 체크리스트다. 실제 개인정보나 실제
기관 제출 자료를 사용하지 않는다.

## 시연 목표

```text
HR 자연어 요청
→ AI가 EXPIRY_RENEWAL / WF-STY-001 선택
→ HR이 후보 채택
→ CASE-EXPIRY-RENEWAL-001 Case와 업무 4개 생성
→ HR이 재계약 조건과 계약서 초안 검토
→ 근로자에게 ARC 보완 링크 전달
→ 근로자가 합성 ARC 제출
→ HR이 제출 파일 채택·OCR 검토
→ 취업활동기간 연장과 체류기간 연장을 차례로 기록
→ 모든 업무와 Case 완료
```

외부기관 로그인과 실제 제출은 자동화하지 않는다. HR이 실제 실행 결과를 기록하는
지점까지만 FOWOCO가 관리한다.

## 1. 시작 전 준비

### Demo Seed만 빠르게 실행

```bash
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='<12자 이상 로컬 합성 비밀번호>'
./gradlew bootRun
```

기본 `local` profile은 H2 인메모리 DB를 사용한다. 서버를 종료하면 시연 중 만든
Case와 Task도 초기화된다.

### 제출 파일까지 준비하는 PostgreSQL 통합 실행

민감한 네 환경변수는 터미널에만 설정하고 Git, Issue, PR에 값을 남기지 않는다.

```bash
export DEMO_DB_PASSWORD='<local-secret>'
export JWT_SECRET_BASE64='<32-byte-base64-secret>'
export DEMO_SEED_ADMIN_PASSWORD='<12자 이상 로컬 합성 비밀번호>'
export OCR_RESULT_ENCRYPTION_KEY_BASE64='<32-byte-base64-secret>'

./scripts/demo-data import
./scripts/demo-data verify
```

이 명령은 PostgreSQL 16, Server와 합성 이미지·PDF·HWP·HWPX 파일을 준비한다. 같은
명령을 다시 실행해도 예약 데이터가 중복 생성되지 않는다.

준비 완료 기준은 다음과 같다.

- Server: `http://127.0.0.1:8080/actuator/health`
- Swagger: `http://127.0.0.1:8080/swagger-ui/index.html`
- Client: `http://127.0.0.1:5173`
- AI Runtime: `http://127.0.0.1:8000/docs`
- 대표 HR: `demo.hr01@example.com`
- 비밀번호: `DEMO_SEED_ADMIN_PASSWORD`에 설정한 값
- 대표 근로자: `응웬반A`, ID `92000000-0000-0000-0000-000000000006`

> Client의 자연어 분석 화면 연결은 Client Draft PR #360을 사용자 검수 후 반영해야
> 한다. 그 전에도 Swagger의 같은 API로 Server 흐름을 검증할 수 있다.

## 2. HR이 업무를 시작한다

1. 대표 HR 계정으로 로그인한다.
2. Today 화면의 자연어 요청 입력창에 다음 문장을 입력한다.

   > 응웬반A의 재계약과 취업활동기간·체류기간 연장을 준비해줘

3. AI가 목표 완료일을 질문하면 오늘 이후의 날짜를 선택해 답한다.
4. 분석 결과가 다음 값인지 확인한다.

   - Intent: `EXPIRY_RENEWAL`
   - Workflow: `WF-STY-001`
   - 대상 근로자: `응웬반A`

5. 후보를 채택한다. 이때만 Case와 Task가 만들어진다.

화면 연결을 확인할 때 사용하는 Server API는 다음과 같다.

```text
POST /api/v1/ai-runs
POST /api/v1/ai-runs/{aiRunId}/answers
POST /api/v1/ai-runs/{aiRunId}/candidate-decisions
GET  /api/v1/ai-runs/{aiRunId}
GET  /api/v1/ai-runs/{aiRunId}/events
```

생성 결과는 하나의 `caseId` 아래 다음 순서를 가져야 한다.

| 순서 | 업무 | 시작 조건 |
| ---: | --- | --- |
| 1 | 재계약 의사와 근로조건 확정 | 즉시 시작 |
| 2 | 신분서류 보완 | 여권 또는 ARC가 누락·만료·검토 필요할 때만 생성 |
| 3 | 취업활동기간 연장 | 1번과, 생성된 경우 2번 완료 후 |
| 4 | 체류기간 연장 | 3번과, 생성된 경우 2번 완료 후 |

Demo Seed의 응웬반A는 여권이 유효하고 ARC가 누락되어 있으므로 네 업무가 모두 생성되어야
한다. Case 상세는 `GET /api/v1/cases/{caseId}/projection`으로도 확인할 수 있다.

## 3. 재계약 조건과 계약서 초안을 검토한다

첫 업무에서 다음 항목을 HR이 확인하거나 입력한다.

- 재계약 의사와 새 계약기간
- 임금, 근로시간·휴게시간
- 근무 장소와 업무 내용
- 숙식 제공 조건
- 최신 표준근로계약서 양식 사용 여부
- 고용허가기간 연장 적용 여부

필수 체크리스트를 완료한 뒤 Renewal을 실행한다. AI 결과는 자동 승인·자동 발송되지
않으며, Server가 기존 Task, 안내 초안, 생성 문서에 연결한다.

```text
POST /api/v1/tasks/{taskId}/renewal-run
GET  /api/v1/tasks/{taskId}/document-request-draft
```

HR은 생성된 계약서의 주요 값과 원본 근거를 확인하고 승인한다. 서명본 또는 HR 확인을
증빙으로 남긴 뒤 첫 업무를 완료한다.

```text
POST /api/v1/tasks/{taskId}/approval-requests
POST /api/v1/tasks/{taskId}/approve
POST /api/v1/tasks/{taskId}/evidence
POST /api/v1/tasks/{taskId}/complete
```

## 4. 근로자에게 ARC 보완을 요청한다

두 번째 업무에서 안내 초안을 HR이 확인한 뒤 Worker Link를 발급한다. SMS Provider를
사용하지 않는 로컬 시연에서는 응답의 공개 URL을 브라우저 시크릿 창에 직접 연다.

```text
POST /api/v1/tasks/{taskId}/worker-link
GET  /api/v1/tasks/{taskId}/worker-link/delivery
```

실제 SMS 시험은 승인된 발신번호와 테스트 수신번호가 설정된 경우에만 실행한다.

```text
POST /api/v1/worker-links/{workerLinkId}/sms-deliveries
```

근로자 화면에는 내부 key 대신 `외국인등록증 사본`처럼 이해할 수 있는 이름, 쉬운 한국어와
대상 언어 안내, 제출기한이 보여야 한다. 안내 생성에 실패한 상태에서는 자동 발송하지 않는다.

## 5. 근로자 역할로 합성 서류를 제출한다

1. 공개 Worker Link를 시크릿 창 또는 다른 브라우저에서 연다.
2. 안내 내용을 확인한다.
3. 요청 서류에서 `외국인등록증 사본`을 선택한다.
4. Demo Data가 생성한 응웬반A의 합성 ARC 앞면 PNG를 선택한다.
5. 제출 버튼을 누르고 완료 화면을 확인한다.

합성 파일에는 `DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION` 표시가 있으며 실제 신분증이
아니다. 공개 API는 JWT 대신 만료되는 Worker Link token만 사용한다.

```text
GET  /api/v1/public/worker-links/{token}
POST /api/v1/public/worker-links/{token}/files
POST /api/v1/public/worker-links/{token}/responses
```

같은 응답을 다시 제출하거나 새로고침해도 공식 문서가 중복 생성되지 않아야 한다. 만료된
링크는 제출을 거부하고 HR에게 재발급을 요청하도록 안내해야 한다.

## 6. HR이 제출물을 회수하고 OCR을 검토한다

1. HR 화면으로 돌아와 근로자 응답과 파일을 연다.
2. 파일이 응웬반A의 합성 ARC인지 확인한다.
3. 제출 파일을 공식 `WorkerDocument`로 채택한다.
4. OCR 상태가 완료될 때까지 조회한다.
5. OCR 원본과 추출값을 비교하고 필요한 값만 수정한 뒤 검토 완료한다.

```text
GET  /api/v1/tasks/{taskId}/worker-responses
POST /api/v1/tasks/{taskId}/worker-responses/{responseId}/documents/adopt
POST /api/v1/documents/{documentId}/ocr-runs
GET  /api/v1/documents/{documentId}/ocr-runs/latest
POST /api/v1/documents/{documentId}/ocr-runs/{ocrRunId}/review
```

OCR 결과만으로 Worker의 개인정보를 자동 수정하지 않는다. HR이 승인한 OCR Context만
기존 업무를 재실행할 때 사용한다. 같은 파일·이벤트를 다시 처리해도 기존 OCR Run과
WorkerDocument를 재사용해야 한다.

## 7. 취업활동기간과 체류기간 연장을 마친다

앞선 의존 업무가 완료되면 Case의 `currentTask`가 세 번째 업무로 이동해야 한다.

### 취업활동기간 연장

- 별지 제12호의3 초안과 첨부서류를 검토한다.
- HR 승인 후 고용 관련 기관에 직접 제출한다.
- 접수처, 안전한 합성 접수번호, 제출시각을 기록한다.
- 접수증을 증빙으로 남기고 업무를 완료한다.

### 체류기간 연장

- 통합신청서와 체류 관련 증빙을 검토한다.
- HR 승인 후 공식 사이트 또는 관할기관에서 직접 제출한다.
- 접수 결과와 최종 처리 결과를 기록한다.
- 증빙을 남기고 마지막 업무를 완료한다.

```text
POST /api/v1/tasks/{taskId}/external-submissions
POST /api/v1/tasks/{taskId}/evidence
POST /api/v1/tasks/{taskId}/complete
```

## 8. 최종 완료 기준

- Case에 생성된 Task가 Catalog 순서와 의존성을 유지한다.
- 조건부 신분서류 업무는 실제 문서 상태에 따라 생성된다.
- 근로자 제출 파일이 다른 회사나 다른 근로자에게 연결되지 않는다.
- HR 승인 없이 안내·SMS·문서·외부 제출이 자동 실행되지 않는다.
- 생성 HWP/HWPX/PDF를 미리보기 또는 다운로드할 수 있다.
- 모든 외부 제출과 완료에는 증빙과 Audit Event가 남는다.
- 마지막 Task 완료 후 Case 진행률은 100%이고 완료 상태로 조회된다.
- 같은 Idempotency-Key와 같은 이벤트를 재실행해도 Case, Task, 문서가 중복되지 않는다.

## 9. 문제가 생겼을 때 확인 순서

| 증상 | 먼저 확인할 값 |
| --- | --- |
| 대상 근로자를 찾지 못함 | 발화문 이름, `응웬반A` 조회 결과, Server #206 반영 여부 |
| 추가정보 답변이 저장되지 않음 | `POST .../answers`의 실제 HTTP status와 `expected_version` |
| Case에 업무가 2개만 보임 | Knowledge #60과 Server Golden Flow PR 반영 여부 |
| 신분서류 업무가 없음 | 응웬반A ARC 상태가 `MISSING`인지 확인 |
| 모바일 링크가 열리지 않음 | 원본 token, 링크 만료시각, public path 확인 |
| 제출 파일이 안 보임 | Worker Response와 StoredFile 저장 여부, HR 사업장 권한 확인 |
| OCR이 계속 대기 | Outbox backlog, OCR Provider 활성화, 암호화 키 확인 |
| 다음 업무가 열리지 않음 | 선행 Task들의 `COMPLETED`, 생성된 조건부 업무 완료 여부 |
| 문서가 생성되지 않음 | Renewal 응답 `missingSlots`, 생성 문서 status와 FileStorage 경로 확인 |

Server 로그와 Prometheus 측정 방법은
[AI 파이프라인 관측 가이드](ai-pipeline-observability.md)를 따른다.
