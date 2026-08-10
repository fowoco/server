# Demo Seed Fixture Manifest

이 문서는 Figma Showcase 화면과 Golden Flow 시연이 어떤 Server Demo Seed를 사용하는지
기록한다. 화면 명세를 새로운 Server Domain으로 간주하지 않으며, 현재 Flyway 스키마와
API가 지원하는 fixture만 나열한다.

수량, 실행, 초기화와 PostgreSQL 검증 절차는
[Demo Seed 운영 시나리오](demo-seed.md)를 기준으로 한다.

## 데이터 목적 구분

| 구분 | 목적 | 응웬반A 포함 방식 |
| --- | --- | --- |
| Golden Flow 시작 데이터 | HR 자연어 요청부터 실제 흐름 시연 | Worker와 최소 문서 상태·선행 참조만 존재 |
| Showcase Seed | 목록·업무함·문서함의 상태 다양성과 화면 밀도 | 응웬반A의 진행 데이터는 포함하지 않음 |

다른 27명 근로자의 기존 Showcase WorkerDocument, Case, Task, Approval, Submission,
Evidence, Audit와 고정 ID는 유지한다.

## Golden Flow 시작 Worker

| fixture | 결정적 식별자·값 | 시연 시작 시 상태 | 현재 노출 |
| --- | --- | --- | --- |
| Company | `90000000-0000-0000-0000-000000000001` | `FOWOCO Demo Company`, `ACTIVE` | 인증·tenant 범위 |
| HR 사용자 | `demo.hr01@example.com` | 같은 Company의 `HR` | 로그인 및 권한 기반 API |
| Worker | `92000000-0000-0000-0000-000000000006` | 응웬반A, `VN`, `vi`, `ACTIVE`, 체류 만료 `D+45` | `GET /api/v1/workers`, `GET /api/v1/workers/{workerId}` |
| 여권 사본 | `95000000-0000-0000-0000-000000000016` | `PASSPORT_COPY`, `VERIFIED`, 만료 `D+365` | 문서 API·AI Context |
| 외국인등록증 사본 | `95000000-0000-0000-0000-000000000017` | `ARC`, `MISSING`, 만료일 없음 | 문서 API·AI Context |
| Workflow Catalog | version `0.2.0` | classpath projection 사용 가능 | Workflow 및 AI 요청 흐름 |

`D+45`는 현재 Worker 모델에 저장되는 상대 날짜 예시다. 정확한 E-9 취업활동기간이나
3년 만료 의미를 나타내는 전용 필드가 아니며 관련 모델과 판정은 Issue #84 범위다.

별도 Workplace, 연락처, 활성 Agent Version 또는 활성 Prompt Version Seed는 없다.
Agent·Prompt 정보는 실제 실행 후 `AiAttempt` 메타데이터로 기록된다.

두 문서는 Task나 StoredFile에 연결하지 않는다. `VERIFIED` 여권은 검증 상태와 유효한
만료일만 나타내며 OCR 결과나 여권번호가 있다는 뜻이 아니다. `MISSING` ARC row는 필요한
문서가 현재 누락된 상태임을 row 부재와 구분한다. Server는 AI Runtime이 요청한 경우에만
이 네 가지 문서 상태·만료일 필드를 같은 tenant 범위의 Context로 제공한다.

## 응웬반A의 Golden Flow 경계

| 영역 | 시작 상태 |
| --- | --- |
| AI 실행 | `AiRun`, `AiAttempt`, Question, Candidate, Candidate Decision 0건 |
| Case·Task | 응웬반A Case 0건, Task 0건 |
| Task 후속 상태 | Checklist, Approval, Transition, Draft, 완료 상태 0건 |
| 근로자 문서 | 여권·ARC 상태 2건; 계약서·StoredFile·OCR fixture 없음 |
| 근로자 통신 | WorkerLink, WorkerResponse 0건 |
| 제출·증빙 | ExternalSubmission, Evidence 0건 |
| Activity·Audit | 대표 흐름 관련 Event 0건 |

Worker Link와 Worker Response API가 구현되어 있어도 Seed는 응웬반A의 발급·응답 완료
상태를 미리 만들지 않는다. 실제 시연 중 생성되는 식별자도 Seed가 예약하지 않는다.

## 구버전 Golden Flow fixture

다음 ID는 과거 응웬반A의 완료·진행 예시에 사용됐지만 현재 생성 대상에서 제외된다.
다른 Worker로 재연결하거나 이후 Showcase ID를 재번호화하지 않는다.

| 데이터 | 제외된 고정 ID |
| --- | --- |
| Case | `94100000-0000-0000-0000-000000000006` |
| Task | `94000000-0000-0000-0000-000000000006` ~ `...0008` |
| WorkerDocument | `95000000-0000-0000-0000-000000000018` |
| Checklist | `94200000-0000-0000-0000-000000000015` ~ `...0022` |
| Approval | `94300000-0000-0000-0000-000000000002` |
| Transition | `94400000-0000-0000-0000-000000000013` ~ `...0016` |
| Document Request Draft | `94700000-0000-0000-0000-000000000002` |
| Audit Event | `96000000-0000-0000-0000-000000000024` ~ `...0028`, `...0045`, `...0083`, `...0094` |

구버전 개인 DB에 이 ID가 남아 있으면 Seed는 자동 삭제하지 않고 기동을 중단한다.
개인 Demo DB 또는 전용 volume을 초기화해야 한다.

## 현재 Showcase 수량

| fixture | Demo Company 수량 | 비고 |
| --- | ---: | --- |
| Worker | 28 | 응웬반A 1명과 Showcase 근로자 27명 |
| Case | 21 | 다른 근로자의 Showcase Case |
| Task | 21 | 체류연장 9, 재계약 7, 고용기간 연장 5 |
| WorkerDocument | 83 | 여권 26, ARC 28, 계약서 21, 허가서 8 |
| Checklist | 60 | Showcase Task 연결 |
| Approval | 12 | 대기 3, 승인 7, 반려 1, 무효 1 |
| Transition | 48 | Showcase 상태 전이 |
| ExternalSubmission | 6 | 합성 참조 번호만 사용 |
| Evidence | 10 | 합성 문서와 안전한 메모 |
| Document Request Draft | 4 | 다른 근로자의 다국어 초안 |
| Audit Event | 88 | HR 77, AI 2, 시스템 6, Worker Link 3 |
| StoredFile | 3 | 합성 PDF |

Catalog는 구버전 전체 목록의 순번을 유지한 채 응웬반A의 여권·ARC는 Task 연결을 제거해
시작 Context로 남기고, 계약서와 나머지 진행 closure만 제외한다. 따라서 제외 지점 뒤에
있는 다른 Worker의 파생 ID와 연결 관계는 바뀌지 않는다.

## Showcase 상태 분포

| 화면 표현 | Server 상태·fixture |
| --- | --- |
| 승인 검토 | `READY_FOR_REVIEW` 3건, `PENDING` Approval 3건 |
| AI 제안·정보 보완 | `DRAFT` 2건, `NEEDS_INFO` 2건 |
| 근로자 대기 | `WAITING_WORKER` 3건과 문서 요청 Draft·Audit 근사 |
| 외부 처리 대기 | `WAITING_EXTERNAL` 3건 |
| 완료·취소 | `COMPLETED` 5건, `CANCELLED` 1건과 관련 Evidence·Audit |
| 문서 검토 | `VERIFIED` 47, `SUBMITTED` 20, `MISSING` 16 |

Dashboard 수치는 별도 Seed Domain이 아니라 조회 결과를 클라이언트가 조합한다. Audit의
`WORKER_LINK` actor 예시는 Showcase 활동 표현이며 실제 Worker Link token row를 Seed한
것은 아니다.

## 합성 PDF와 연결

모든 PDF에는 `DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION` 표시가 있고 실제 개인정보나
행정 문서 원본을 포함하지 않는다.

| 파일 | StoredFile ID | Showcase 연결 |
| --- | --- | --- |
| `demo-contract-renewal.pdf` | `94800000-0000-0000-0000-000000000001` | 마크 레예스의 계약 문서·Task |
| `demo-stay-extension-receipt.pdf` | `94800000-0000-0000-0000-000000000002` | 모하메드 라힘의 RECEIPT Evidence |
| `demo-stay-extension-result.pdf` | `94800000-0000-0000-0000-000000000003` | 모하메드 라힘의 OFFICIAL_RESULT Evidence |

Demo Seed는 classpath PDF를 `app.file-storage.local-path`에 설치한다. 같은 storage key의
일반 파일은 hash와 크기가 같으면 재사용하며, 예약 ID·소유권·메타데이터가 충돌하면
덮어쓰지 않고 기동을 중단한다. 권한이 있는 사용자는
`GET /api/v1/files/{fileId}/content`로 파일 내용을 조회할 수 있다.

## API 노출 요약

| 데이터 | 저장 | 조회 API | 비고 |
| --- | --- | --- | --- |
| Worker | O | O | 이름·국적·언어·현재 날짜 필드 |
| Case와 Workflow Snapshot | O | O | 진행률·현재 Task·준비도 요약 |
| Task와 Checklist | O | O | `case_id`, business data, 상태·마감일 |
| WorkerDocument | O | O | 문서 상태·만료일·선택적 `file_id` |
| Audit Event | O | O | 업무 활동과 ADMIN 감사 검색 |
| WorkerLink·WorkerResponse | Golden Flow에서 X | O | 시연 과정에서 생성·조회 |
| Approval | Showcase에 O | write 중심 | 요청·결정 흐름 |
| ExternalSubmission·Evidence | Showcase에 O | write 중심 | 시연 과정 기록 |
| Document Request Draft | Showcase에 O | X | 조회 Controller 없음 |
| StoredFile content | Showcase에 O | O | 권한 기반 content 조회 |

## 의도적으로 포함하지 않는 데이터

- OCR 상태·결과·신분증 이미지
- 여권번호, 외국인등록번호, 전화번호, 주소, 임금
- 실제 Worker Link token, 계정 token, API Key와 Secret
- #84의 E-9 날짜·비자정보 필드와 판정 결과
- 별도 Workplace, 연락처, Agent/Prompt Version Registry
- HWP/HWPX와 실제 행정 제출 문서
