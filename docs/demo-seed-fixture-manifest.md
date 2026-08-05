# Figma Demo Fixture Manifest

이 문서는 [FOWOCO Figma의 PWF_v3 흐름](https://www.figma.com/design/eaOD8OXZOGq6vK4H9pGXNi/FOWOCO?timeline=keyframe&node-id=1246-751&p=f)을
서버 Demo Seed가 어떤 데이터로 표현하는지와, 그 데이터가 현재 API에서 어디까지
노출되는지를 기록한다. 이 manifest는 화면 명세를 새로운 API 계약으로 간주하지
않으며, 기존 Flyway 스키마와 애플리케이션 계약이 수용하는 fixture만 다룬다.

## 상태 표기

| 상태 | 의미 |
| --- | --- |
| API 노출 | 현재 조회 API 응답으로 확인할 수 있다. |
| 저장 전용 | DB 또는 로컬 파일 저장소에는 있지만 현재 조회 API가 없다. |
| 근사 | 별도 모델 없이 기존 상태나 식별자로 화면 의미를 표현한다. |
| 제외 | 현재 도메인·스키마로 만들지 않거나 의도적으로 저장하지 않는다. |

## 대표 복합 초안 흐름

Figma의 재계약·고용기간 연장·체류기간 연장 초안 흐름은 응웬반A와 동일한
`case_id`를 사용하는 세 업무로 구성한다.

| Figma 의미 | 예약 fixture | 저장 내용 | 현재 노출 |
| --- | --- | --- | --- |
| 대상 근로자 | Worker `92000000-0000-0000-0000-000000000006` | 응웬반A, `VN`, `vi`, 체류 만료 `D+45` | `GET /api/v1/workers`, `GET /api/v1/workers/{workerId}` |
| 복합 Case | Case ID `94100000-0000-0000-0000-000000000006` | 세 업무의 연속성과 생성 당시 Workflow Snapshot | Case 목록·Projection API와 Task 응답의 `case_id`로 노출 |
| 재계약 조건 검토 | Task `94000000-0000-0000-0000-000000000006` | `RECONTRACT`, `READY_FOR_REVIEW`, candidate order 1 | Task 목록·상세 API |
| 후행 고용기간 연장 | Task `94000000-0000-0000-0000-000000000007` | `DRAFT`, candidate order 3, Task 6 의존 | Task 목록·상세 API |
| 여권 사본 요청 | Task `94000000-0000-0000-0000-000000000008` | `WAITING_WORKER`, candidate order 2, 제출 기한 7일 | Task 목록·상세 API |
| 검토 snapshot | Approval `94300000-0000-0000-0000-000000000002` | AI/HR snapshot, 7/7 검증, 경고 1건, HR 변경 필드와 원천 버전 | 저장 전용; 승인 조회 API 없음 |
| 베트남어 요청 초안 | Draft `94700000-0000-0000-0000-000000000002` | `vi`, `PASSPORT_COPY`, 7일 이내 제출 문구 | 저장 전용; Draft 조회 API 없음 |
| AI 처리 흔적 | trace ID `demo-compound-draft-flow` | 대상 확인부터 후행 후보 준비까지 AI Agent 이벤트 5건 | 업무 활동 API에서 노출 |

Case 진행률과 표시 상태는 `GET /api/v1/cases`에서 확인하고, 세 Task와 준비도 요약은
`GET /api/v1/cases/{caseId}/projection`에서 확인한다.

## 검토·승인·제출 lifecycle

| 단계 | fixture | 저장 내용 | 현재 노출 |
| --- | --- | --- | --- |
| 승인 대기 | Approval 1~4 | `PENDING` 4건과 업무별 immutable snapshot | 저장 전용 |
| 승인 완료 | Approval 5~11 | `APPROVED` 7건과 결정 사유 | 저장 전용 |
| 반려 | Approval 12 | 필수 고용 정보 부족으로 `REJECTED` | 저장 전용 |
| 무효화 | Approval 13 | 마감일 변경으로 기존 snapshot `INVALIDATED` | 저장 전용 |
| 외부 제출 | External Submission 1~6 | 고용센터·출입국 제출처와 안전한 참조 번호 | 저장 전용 |
| 완료 증빙 | Evidence 1~10 | 문서·HR 확인·접수증·공식 결과 | 저장 전용 |
| 화면 활동 | Audit Event 96건 | 승인·반려·제출·증빙·완료 이벤트 | 업무 활동 및 ADMIN 감사 검색 API |

현재 Approval API는 승인 요청과 결정, 제출·증빙 기록을 위한 write API다. 기존
fixture를 읽는 Approval·Submission·Evidence 조회 API는 없으므로, 화면에서는 Task
상태와 Audit Event만 직접 사용할 수 있다.

## 합성 PDF와 문서 연결

PDF는 모두 합성 데이터이며 `DEMO / SAMPLE - NOT FOR OFFICIAL SUBMISSION` 표시가
있다. 실제 개인정보나 행정 문서 원본을 포함하지 않는다.

| 파일 | StoredFile ID | 연결 | 현재 노출 |
| --- | --- | --- | --- |
| `demo-contract-renewal.pdf` | `94800000-0000-0000-0000-000000000001` | 마크 레예스, Task 5, CONTRACT 문서 `95000000-0000-0000-0000-000000000007` | 통합 문서함의 `file_id`; 파일 읽기·다운로드 API는 없음 |
| `demo-stay-extension-receipt.pdf` | `94800000-0000-0000-0000-000000000002` | 모하메드 라힘, Task 20, RECEIPT Evidence | 저장 전용 |
| `demo-stay-extension-result.pdf` | `94800000-0000-0000-0000-000000000003` | 모하메드 라힘, Task 20, OFFICIAL_RESULT Evidence | 저장 전용 |

`task_evidence.file_reference`에는 prefix 없는 `stored_file_id` UUID 문자열을
저장한다. DB FK가 아니므로 Demo Seed verifier가 Task·Worker·파일 관계를 직접
검증한다.

Demo Seed가 활성화되면 classpath PDF를 `app.file-storage.local-path`에 설치한다.
동일한 storage key의 파일은 크기와 SHA-256이 같을 때만 재사용하며, 다른 파일은
덮어쓰지 않고 시작을 중단한다. 파일만 남거나 DB row만 남은 상태는 동일 fixture인
경우 복구한다. 현재 installer는 `LocalFileStorage` 구성만 지원한다.

## 근로자와 지원 언어

Demo Company의 근로자 28명은 AI 팀이 지원하는 locale 15개를 모두 최소 1명씩
포함한다.

`en`, `zh-Hans`, `vi`, `th`, `fil`, `id`, `mn`, `si`, `ru`, `uz`, `ky`, `bn`,
`ur`, `km`, `tet`

국적·선호 언어 조합은 데모에서 실제 사용할 법한 조합으로 구성한다. Worker UUID,
업무·문서 연결, 상태와 상대 날짜는 locale 정합화 전후에 유지한다.

## API 노출 요약

| 데이터 | 저장 | 조회 API | 비고 |
| --- | --- | --- | --- |
| Worker | O | O | 이름·국적·선호 언어·체류/계약일 노출 |
| Case와 Workflow Snapshot | O | O | 진행률·현재 Task·준비도 요약 노출 |
| Task와 Checklist | O | O | `case_id`, `business_data`, 상태와 마감일 노출 |
| WorkerDocument | O | O | 통합 문서함에서 상태·만료일·선택적 `file_id` 노출 |
| Audit Event | O | O | 업무 활동과 ADMIN 감사 검색 지원 |
| Approval snapshot | O | X | write API만 존재 |
| External Submission | O | X | write API만 존재 |
| Evidence | O | X | write API만 존재 |
| Document Request Draft | O | X | 조회 Controller 없음 |
| StoredFile 메타데이터 | O | X | 업로드 API만 존재 |
| PDF 바이너리 | O | X | 로컬 저장소에 설치되지만 읽기·다운로드 API 없음 |

## 근사 및 제외 범위

- 문서 요청·응답 대기는 `WAITING_WORKER`, `MISSING`, Draft와 Audit로 근사한다.
- 요청 중과 최근 업로드는 각각 `MISSING`, `SUBMITTED` 상태로 근사한다.
- Dashboard 수치는 별도 집계 API가 아니라 클라이언트가 조회 결과를 조합한다.
- Worker Secure Link의 토큰·전송·읽음·응답 수명주기는 만들지 않는다.
- HWP/HWPX, 여권·외국인등록증 이미지와 실제 행정 제출 문서는 포함하지 않는다.
- 여권번호, 외국인등록번호, 전화번호, 주소, 임금, 계정·토큰과 실제 Secret은
  저장하지 않는다.

수량, 실행 방법, 초기화와 멱등성 규칙은 [Demo Seed 운영 시나리오](demo-seed.md)를
기준으로 한다.
