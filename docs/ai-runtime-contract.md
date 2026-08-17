# Server ↔ AI Runtime 계약 기반

이 문서는 `fowoco/server`가 별도 배포되는 `fowoco/ai` Runtime을 호출할 때 지켜야 하는
최소 계약과 방어 규칙을 설명합니다.

현재 Server가 고정한 Knowledge projection과 AI Runtime의 기준 버전은 `0.3.0`입니다.

Server에는 `/internal/v1/analyses`를 호출하는 HTTP Adapter까지 구현되어 있습니다.
다만 실제 OpenAPI와 Structured Output JSON Schema의 원본은 `fowoco/ai`가 소유하므로,
AI 저장소에서 같은 `contractVersion`을 release하기 전까지 실제 호출은 기본적으로 꺼 둡니다.

## 초보자용 한 줄 설명

AI 서버에 무엇을 보낼 수 있는지 먼저 좁혀 놓고, AI가 돌려준 값도 그대로 믿지 않고 다시
검사하는 안전문입니다.

```text
AiRunWorker (#24, 후속)
  → ValidatingAiRuntimeClient
      1. 요청 크기·Service credential 검사
      2. AiRuntimeClient transport를 정확히 한 번 호출
      3. 응답 ID·version·worker·workflow·slot 재검사
  → FakeAiRuntimeClient (test)
  → RemoteAiRuntimeClient
      1. deadline·bulkhead·circuit breaker 적용
      2. Bearer 인증과 추적 header 전달
      3. 응답 크기 제한과 strict JSON parsing
      → POST /internal/v1/analyses (fowoco/ai)
```

`AiRuntimeClient`는 OpenAI, Gemini, Anthropic 같은 Provider를 직접 호출하지 않습니다.
Prompt, Agent Pipeline, Provider retry와 모델 선택은 `fowoco/ai` 책임입니다.

## PLAN 요청 계약

첫 호출은 HR 발화문을 이해하고 Server에 필요한 DB field를 요청하는 단계입니다. 화면의
빠른 선택 태그는 입력 예시를 채우는 UI 기능일 뿐, API 데이터가 아닙니다. Client는 사용자가
최종 작성한 발화문만 `instruction`으로 보내고, Server도 이를 그대로 Runtime에 전달합니다.
`intentHint`를 보내거나 `instruction` 뒤에 Intent 코드를 붙이지 않습니다. 최종 분류 결과는
Runtime이 반환한 `detectedIntent`를 사용합니다. 이 단계에는 Worker UUID나 DB 조회값을
넣지 않습니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "phase": "PLAN",
  "analysisInput": {
    "instruction": "응웬반안 체류연장 준비해줘"
  }
}
```

Runtime에는 `requestId`, `phase`, `analysisInput`만 전송합니다. PLAN에서 아직 값이 없는
`requestedFieldKeys`와 `workers`는 JSON에 보내지 않습니다. `attemptId`, version, deadline은
Server 내부에서만 관리합니다.

Runtime이 DB 정보를 더 필요로 하면 성공 응답으로 `CONTEXT_REQUIRED`를 반환합니다.
Agent는 SQL을 만들거나 DB를 직접 조회하지 않고, canonical field key만 요청합니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "outcome": "CONTEXT_REQUIRED",
  "contextRequirement": {
    "detectedIntent": "EXPIRY_RENEWAL",
    "workflowId": "WF-STY-001",
    "evidence": "체류연장 준비해줘",
    "confidence": null,
    "confidenceSource": "UNAVAILABLE",
    "bertRoutingScore": 0.3088,
    "targetDisplayName": "응웬반안",
    "extractedSlots": {},
    "requiredFieldKeys": [
      "worker_id",
      "due_at",
      "stay_expiry_date",
      "passport_status",
      "arc_status"
    ]
  },
  "questions": [],
  "candidates": [],
  "validationErrors": [],
  "versions": {
    "agentVersion": "agent-1.0.0",
    "modelProvider": "openai",
    "modelName": "gpt-5-mini",
    "modelVersion": "2026-07-01",
    "promptVersion": "prompt-3",
    "contextPackVersion": "0.3.0",
    "workflowCatalogVersion": "0.3.0",
    "contractVersion": "1.1.0"
  },
  "providerAttemptCount": 1,
  "latencyMs": 120
}
```

MVP에서는 발화 하나에서 **대표 Intent와 Workflow 한 쌍만** 선택합니다. 복합 Intent를
여러 업무로 나누는 기능은 후속 범위입니다. A.X처럼 분류 확률을 제공하지 않는 모델은
`confidence=null`, `confidenceSource=UNAVAILABLE`을 반환합니다. PLAN 전에 사용한 BERT
라우팅 점수는 A.X confidence로 위장하지 않고 `bertRoutingScore`에만 기록합니다.
`evidence`는 일반 Slot이 아니므로 `extractedSlots`에 `evidence:*` 같은 가짜 key로 넣지 않습니다.

발화가 지원 업무가 아니면 Runtime은 DB field나 Workflow를 억지로 만들지 않고 PLAN에서
`OUT_OF_SCOPE`로 정상 종료합니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "outcome": "OUT_OF_SCOPE",
  "contextRequirement": null,
  "questions": [],
  "candidates": [],
  "validationErrors": [],
  "versions": {
    "agentVersion": "agent-1.0.0",
    "modelProvider": "huggingface",
    "modelName": "klue-roberta-base",
    "modelVersion": "BERT",
    "promptVersion": "knowledge-25e778ad",
    "contextPackVersion": "0.3.0",
    "workflowCatalogVersion": "0.3.0",
    "contractVersion": "1.1.0"
  },
  "providerAttemptCount": 1,
  "latencyMs": 80
}
```

Server는 이를 `SUCCEEDED + OUT_OF_SCOPE`로 저장하고 공개 SSE를 `COMPLETED`로 끝냅니다.
Slot 조회와 ANALYZE 호출은 수행하지 않습니다. 따라서 지원하지 않는 발화가 빈
`workflowId`나 임의의 Workflow로 실행되는 일도 없습니다.

## ANALYZE 요청 계약

#74가 `targetDisplayName`을 현재 사업장 안에서 한 명의 Worker로 찾고, 허용된
`requiredFieldKeys`만 Repository로 조회합니다. Server는 새 `attemptId`를 내부에 기록한
뒤 같은 `requestId`로 ANALYZE를 호출합니다. MVP에서는 한 요청에 Worker 한 명만 허용합니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "phase": "ANALYZE",
  "analysisInput": {
    "instruction": "응웬반안 체류연장 준비해줘",
    "plannedIntent": "EXPIRY_RENEWAL",
    "plannedWorkflowId": "WF-STY-001",
    "requestedFieldKeys": [
      "legal_name",
      "stay_expiry_date"
    ],
    "workers": [
      {
        "workerRef": "30000000-0000-0000-0000-000000000001",
        "requestedFields": {
          "legal_name": "NGUYEN VAN AN",
          "stay_expiry_date": "2026-12-31"
        }
      }
    ]
  }
}
```

- `requestId`: Server 요청과 Runtime 응답을 같은 실행으로 연결합니다.
- `phase`: 발화문을 해석하는 `PLAN`과 Server 보유정보로 결과를 만드는 `ANALYZE`를 구분합니다.
- `instruction`: 사용자가 최종 작성한 HR 발화문 원문입니다. 빠른 선택 태그나 Server가
  추측한 Intent를 덧붙이지 않습니다. 현재 데모에서는 가상 근로자 데이터만 사용합니다.
- `detectedIntent`: Runtime 응답에서만 정해지는 최종 Intent입니다. Server가 발화문이나
  화면 태그를 기준으로 별도 판정하지 않습니다.
- `plannedIntent`, `plannedWorkflowId`: PLAN에서 Runtime이 정한 대표 결과입니다. Server가
  `ai_attempt.analysis_input_json`에 보존한 뒤 ANALYZE에 다시 전달합니다. Runtime은 이 값이
  있으면 Intent 모델을 다시 호출하지 않습니다.
- ANALYZE가 PLAN 결정을 재사용해 Provider를 호출하지 않았다면 `providerAttemptCount=0`을 허용합니다.
- PLAN confidence는 Intent 분류 이력이며 ANALYZE HTTP에 다시 보내지 않습니다. Candidate
  confidence는 별도의 선택값으로 취급하고 PLAN 값과 비교하지 않습니다. 모델을 다시 호출하지
  않았다면 `null`을 반환하며, 값이 있는 경우에만 0 이상 1 이하인지 검증합니다.
- `requestedFieldKeys`: Agent가 PLAN에서 요청했던 전체 key입니다. DB에 값이 없어도 목록에는 남습니다.
- `requestedFields`: Agent가 요구한 field의 원본값입니다. Server가 가진 값만 넣습니다.

`attemptId`, `contractVersion`, `requiredKnowledgeVersion`, `deadlineMs`, `extractedSlots`,
`workflowConstraints`는 Server가 재시도·응답 검증·제한시간을 관리하기 위해 내부
`AiAnalysisRequest`에 유지하지만 HTTP JSON에는 넣지 않습니다.

한 번의 PLAN 또는 ANALYZE 호출 제한시간은 `AI_RUNTIME_OVERALL_TIMEOUT`을 단일 기준으로
사용합니다. 기본값은 실제 A.X CPU 추론 시간을 수용하는 `240s`이고 계약상 최대값은
`5m`입니다. Client 요청은 먼저 `202 + aiRunId`를 반환하므로 이 제한시간 동안 HTTP 화면
요청을 붙잡지 않으며, 진행 상태는 SSE와 조회 API로 제공합니다.

`modelVersion`과 `promptVersion`은 기존 `ai_attempt` 버전 컬럼에 저장합니다. PLAN 결정은
ANALYZE attempt의 `analysis_input_json`에 함께 저장하므로 PLAN 결정 재사용 자체는 새 DB
컬럼을 요구하지 않습니다. `OUT_OF_SCOPE` 저장을 허용하기 위해서는
`V41__add_ai_run_out_of_scope_outcome.sql`이 기존 outcome 체크 제약을 확장합니다.

현재 데모에서는 PII 마스킹과 차단을 적용하지 않습니다. 실명·여권번호·전화번호 등
Agent가 문서 작성에 요구한 값은 `***`, `OOO`로 바꾸지 않고 원본으로 전달합니다.

단, API Key·JWT·Bearer Token·비밀번호·Worker Link token 같은 **서비스 인증정보는
업무 데이터가 아니므로 계속 차단**합니다. 이 계약으로 실제 근로자 데이터를 외부 LLM에
전송해서는 안 되며, 데모가 아닌 실제 개인정보를 사용하기 전에는 개인정보 처리 기준을
다시 확정해야 합니다.

## ANALYZE 응답 계약

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "outcome": "REVIEW_REQUIRED",
  "contextRequirement": null,
  "questions": [],
  "candidates": [
    {
      "candidateRef": "candidate-1",
      "workerRef": "30000000-0000-0000-0000-000000000001",
      "workflowId": "WF-STY-001",
      "extractedSlots": {
        "stay_expiry_date": "2026-12-31"
      },
      "missingSlots": [
        "contract_end_date",
        "monthly_wage"
      ],
      "confidence": null
    }
  ],
  "validationErrors": [],
  "versions": {
    "agentVersion": "agent-1.0.0",
    "modelProvider": "openai",
    "modelName": "gpt-5-mini",
    "modelVersion": "2026-07-01",
    "promptVersion": "prompt-3",
    "contextPackVersion": "0.3.0",
    "workflowCatalogVersion": "0.3.0",
    "contractVersion": "1.1.0"
  },
  "providerAttemptCount": 1,
  "latencyMs": 245
}
```

`OUT_OF_SCOPE`, `CONTEXT_REQUIRED`, `NEEDS_INFO`, `REVIEW_REQUIRED`는 모두 정상 분석 결과이며 AiRun의
기술적 `FAILED` 상태와 섞지 않습니다.

- `OUT_OF_SCOPE`: 지원하지 않는 발화입니다. PLAN에서 종료하며 context·질문·candidate가 없습니다.
- `CONTEXT_REQUIRED`: Server DB에서 조회할 canonical field key가 있습니다.
- `NEEDS_INFO`: DB로 채울 수 없어 HR에게 보여 줄 `questions`가 있습니다.
- `REVIEW_REQUIRED`: 검토 가능한 `candidates`가 있습니다.

Candidate는 Task도 승인도 아니며, #24에서 HR이 채택한 후에만 Server Task command로
전달됩니다.

## 여권 OCR 국가 코드

Worker의 `nationality_code`는 ISO 3166-1 alpha-2 대문자(`VN`, `PH`)를 기준으로 합니다.
여권 OCR 요청의 `countryCode`는 Worker 값을 그대로 보내지 않고, AI가 배포한 국가별
Template을 고를 수 있도록 alpha-3 코드(`VNM`, `PHL`)로 변환합니다.

현재 AI Runtime이 배포한 여권 Template과 Server 변환 범위는 다음 다섯 국가입니다.

| Worker 국적 | OCR 국가 | 지원 상태 |
| --- | --- | --- |
| `KR` | `KOR` | 지원 |
| `PH` | `PHL` | 지원 |
| `JP` | `JPN` | 지원 |
| `CN` | `CHN` | 지원 |
| `VN` | `VNM` | 지원 |

`TH`, `NP`처럼 배포된 여권 Template이 없는 국가는 `UNSUPPORTED_OCR_COUNTRY`로 구분하고
AI를 호출하지 않습니다. 국가를 추가할 때는 AI Template 배포, Server 변환표와 양쪽 계약
테스트를 함께 변경합니다. 외국인등록증(`ARC`) 요청에는 국가 코드를 보내지 않습니다.

## OCR 실행·저장 경계

HR 화면은 AI를 직접 호출하지 않고 다음 Server API를 사용합니다.

| API | 역할 |
| --- | --- |
| `POST /api/v1/documents/{documentId}/ocr-runs` | 실행 이력을 `QUEUED`로 먼저 저장하고 202 반환 |
| `GET /api/v1/documents/{documentId}/ocr-runs/{ocrRunId}` | 실행 상태와 HR 검토용 결과 조회 |
| `GET /api/v1/documents/{documentId}/ocr-runs/latest` | 해당 문서의 최신 실행 조회 |
| `POST /api/v1/documents/{documentId}/ocr-runs/{ocrRunId}/review` | HR의 수정값과 검토 완료·반려 기록 |

근로자 모바일 제출물을 HR이 공식 `WorkerDocument`로 채택하면 여권·외국인등록증은
`WorkerDocumentAdopted` Outbox 이벤트를 통해 OCR 실행이 자동 접수됩니다. 파일 채택과
외부 OCR 호출을 같은 HTTP transaction에 묶지 않으므로, Runtime 장애가 발생해도 채택
결과는 유지되고 OCR 실행 상태를 별도로 재처리할 수 있습니다.

OCR 결과는 자동으로 Worker 개인정보를 수정하지 않습니다. HR이 결과를 대조해 승인하면
`DocumentOcrApproved` 이벤트가 기존 Case의 Renewal Task를 다시 실행합니다. Server는
승인된 OCR 원본과 HR 수정값을 Context에서 병합하고, Agent 결과가 `generate`이면 생성
파일을 기존 `stored_file`과 `worker_document`에 HR 검토용 초안으로 저장합니다.

Server는 연결된 `stored_file`을 읽어 AI의
`POST /internal/v1/ocr/worker-documents/{workerDocumentId}`로 multipart 전송합니다.
AI는 DB에 접근하거나 결과를 저장하지 않습니다. OCR 요청은 실행 이력과 같은 트랜잭션에서
Outbox 이벤트로 저장됩니다. 서버가 중단되면 메모리 작업 대신 DB에 남은 이벤트 lease를
다른 인스턴스가 회수해 다시 실행합니다. 실행 중 중단된 트랜잭션은 `QUEUED`로 롤백되므로
영구적인 `RUNNING` 상태를 만들지 않습니다.

Server는 계약 검증을 통과한 `fields`, `field_confidences`, `review_reasons`를 하나의
AES-256-GCM 암호문으로 저장합니다. HR의 `corrected_fields`는 OCR 원본을 덮어쓰지 않고
별도 암호문으로 저장합니다. 감사로그에는 수정한 field key만 기록하며 여권번호 같은 실제
값은 일반 컬럼·감사로그·오류 메시지에 남기지 않습니다.

`READY_FOR_REVIEW`와 `REVIEW_REQUIRED` 모두 HR 확인 대상입니다. `APPROVE`는 OCR 검토를
완료했다는 뜻이며, Worker·Document·Agent slot을 자동 수정하지 않습니다. 확정값 반영은
별도 command와 권한 정책이 합의된 뒤 연결합니다.

Server는 AI 응답의 허용 field와 confidence뿐 아니라 다음도 다시 검증합니다.

- `SUCCEEDED` 결과의 문서 종류별 필수 field와 ISO `YYYY-MM-DD` 날짜
- 여권 국가와 Template ID의 일치
- ARC Template ID(`43024`, `43025`)와 `FRONT`·`BACK`의 일치
- 빈 `SUCCEEDED` 결과와 모순된 Template·면 정보 거부

## Server가 거부하는 응답

- 요청과 다른 `requestId`
- 요청과 다른 contract 또는 Workflow Catalog version
- 요청에 없던 `workerRef`나 `workflowId`
- PLAN에서 선택한 `plannedWorkflowId`와 다른 ANALYZE Candidate
- Workflow가 허용하지 않은 slot
- 0 미만 또는 1 초과인 Candidate confidence·PLAN confidence·BERT 라우팅 점수
- `confidenceSource=UNAVAILABLE`인데 confidence가 들어 있는 응답
- 중복 candidate reference와 잘못된 outcome 구조
- `OUT_OF_SCOPE`가 ANALYZE에서 반환되거나 context·질문·candidate·validation error를 포함한 응답
- PLAN에 Worker DB context가 포함되거나 ANALYZE에 Worker context가 없는 요청
- `CONTEXT_REQUIRED`인데 field key가 없거나, `NEEDS_INFO`인데 질문이 없는 응답
- API Key·JWT·Bearer Token·비밀번호·Worker Link token 같은 서비스 인증정보

거부 예외에는 발견한 원문을 넣지 않습니다. 앞으로 #24 AiAttempt에는
`AiRuntimeFailureCode`와 `requestId` 같은 안전한 진단값만 저장합니다.

## 테스트와 실제 구현의 차이

- `FakeAiRuntimeClient`: `src/test`에만 있으며 응답이나 예외를 순서대로 예약합니다.
- `ValidatingAiRuntimeClient`: transport 앞뒤에서 같은 방어 검증을 수행합니다.
- `RemoteAiRuntimeClient`: 설정이 켜진 환경에서만 AI Runtime을 HTTP로 한 번 호출합니다.
- `DisabledAiRuntimeClient`: 기본 구현이며, 실수로 호출하면 `RUNTIME_DISABLED`로 즉시
  실패합니다. LM Studio나 모델 Provider로 우회하지 않습니다.

WireMock 계약 테스트는 다음 동작을 검증합니다.

1. `Authorization: Bearer <service-credential>`, `X-Request-Id`, `traceparent` 전달
2. 문서와 같은 camelCase 요청 JSON, `PLAN → CONTEXT_REQUIRED → ANALYZE` 구조 및
   `PLAN → OUT_OF_SCOPE` 단일 호출 종료 사용
3. 알 수 없는 JSON field와 제한보다 큰 응답 거부
4. connect timeout과 요청·응답 전체 deadline
5. circuit breaker와 동시 호출 수 bulkhead
6. HTTP·parsing·contract 오류의 안정적인 `AiRuntimeFailureCode` 분류
7. 실패 응답에도 HTTP 요청이 한 번만 발생하는지 확인

Remote Client는 자동 HTTP retry를 하지 않습니다. 다시 호출하려면 #24가 먼저 새로운
AiAttempt를 DB에 기록해야 합니다.

## 실행 설정

평소 local 실행과 아직 AI 계약이 배포되지 않은 환경에서는 아래 기본값을 유지합니다.

```dotenv
AI_RUNTIME_ENABLED=false
```

AI Runtime 계약이 배포된 통합 환경에서는 배포 Secret과 함께 설정합니다.

```dotenv
AI_RUNTIME_ENABLED=true
AI_RUNTIME_ENDPOINT=https://ai.example.com/internal/v1/analyses
AI_RUNTIME_SERVICE_CREDENTIAL=<배포 환경 Secret>
```

`AI_RUNTIME_SERVICE_CREDENTIAL`은 Git, 로그, 오류 응답에 남기지 않습니다. Server가 표준
`Authorization: Bearer ...` 형식으로 조립합니다. `X-Request-Id`는 분석 요청의
`requestId`와 같고, 상위 요청의 유효한 W3C `traceparent`가 있으면 그대로 전달합니다.

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `AI_RUNTIME_CONNECT_TIMEOUT` | `2s` | AI 서버에 TCP 연결을 맺을 수 있는 최대 시간 |
| `AI_RUNTIME_OVERALL_TIMEOUT` | `15s` | 연결·요청·응답 수신 전체의 Server 상한 |
| `AI_RUNTIME_MAX_RESPONSE_BYTES` | `1048576` | 응답을 메모리에 받기 전 적용하는 최대 크기 |
| `AI_DOCUMENT_GENERATION_ENDPOINT` | `http://127.0.0.1:8000/api/v1/documents/generate` | Renewal 문서 생성 API |
| `AI_DOCUMENT_CONVERSION_ENDPOINT` | `http://127.0.0.1:8000/api/v1/documents/convert` | HWP·HWPX를 PDF 미리보기로 변환하는 API |
| `AI_DOCUMENT_CONVERSION_TIMEOUT` | `60s` | 사용자 요청 안에서 문서 변환을 기다리는 최대 시간 |
| `AI_DOCUMENT_GENERATION_MAX_RESPONSE_BYTES` | `20971520` | 생성 파일을 메모리에 받기 전 적용하는 최대 크기 |
| `AI_RUNTIME_MAX_CONCURRENT_CALLS` | `8` | Server 한 인스턴스가 동시에 보내는 최대 호출 수 |
| `AI_RUNTIME_CIRCUIT_BREAKER_FAILURE_THRESHOLD` | `5` | 연속 장애 후 호출을 잠시 막는 기준 |
| `AI_RUNTIME_CIRCUIT_BREAKER_OPEN_DURATION` | `30s` | 차단 후 시험 호출까지 기다리는 시간 |

Server 내부 요청의 `deadlineMs`와 `AI_RUNTIME_OVERALL_TIMEOUT` 중 더 짧은 값을 HTTP
timeout으로 사용합니다. `deadlineMs` 자체는 Runtime JSON에 전송하지 않습니다. 따라서
상위 AiRun이 허용한 시간보다 오래 기다리지 않습니다.

## Renewal Task와 Workflow 연결

`POST /internal/v1/workflows/renewal/run`에는 이미 생성된 Task의 canonical Workflow를
`task.workflowId`로 전달합니다. Server가 허용하는 조합은 활성 Knowledge Catalog와 같습니다.

| Task type | Workflow ID |
| --- | --- |
| `RECONTRACT` | `WF-CON-001` |
| `EMPLOYMENT_PERIOD_EXTENSION` | `WF-CON-001` |
| `STAY_PERIOD_EXTENSION` | `WF-STY-001` |

Server는 Task type과 Workflow가 위 표와 다르면 AI를 호출하지 않습니다. 정상 Renewal
응답의 `workflowId`도 요청의 `task.workflowId`와 반드시 같아야 합니다. 같은
`EXPIRY_RENEWAL` Intent 안에서 Runtime이 첫 Workflow를 고르거나 발화만 다시 분류해 다른
Workflow로 바꾼 응답은 `UNEXPECTED_WORKFLOW`로 거부합니다.

`scenario=out_of_scope`, `intent=OUT_OF_SCOPE`인 종료 응답만 `workflowId`가 비어 있을 수
있습니다. 이는 Workflow 실행 결과가 아니라 지원 범위 밖 정상 종료 신호이기 때문입니다.

### Renewal Shadow Planning

`AI_RUNTIME_RENEWAL_AGENT_MODE`의 기본값은 `LEGACY`입니다. `SHADOW`로 설정하면 Runtime은
현재 State를 기준으로 Agent 계획을 만들지만, 실제 분기·Task 상태·문서 생성은 계속 기존
Supervisor 결과를 따릅니다. 따라서 Client API와 DB Schema는 변경하지 않습니다.

Runtime은 계획의 각 행동을 `TOOL` 또는 `SERVER_CONTROL`로 구분하고, 제안 Route와 실제
Supervisor Route를 `progressEvents`의 `subgraph=agent-shadow` 이벤트 한 건으로 반환합니다.
Server는 이 구조를 허용 목록으로 재검증한 뒤 기존 `renewal_execution.agent_shadow`에
보존합니다. 계획 불일치는 관측 대상일 뿐 자동 승인·발송이나 업무 분기 변경의 근거가
되지 않습니다.

배포 순서는 Server를 기본 `LEGACY`로 먼저 배포하고 AI Runtime 호환 버전을 배포한 뒤,
검증 환경에서만 `SHADOW`를 켜는 순서입니다. Active Agent 전환은 비교 결과와 E2E가 충분히
쌓인 뒤 별도 결정으로 진행합니다.

## 근로자 안내 실패와 HR 검토

Language Assistant가 비활성화됐거나 안내문을 안전하게 생성하지 못하면 Runtime은 임시
문장을 만들지 않고 다음과 같이 HR 검토를 요청합니다.

```json
{
  "scenario": "ask_worker",
  "status": "READY_FOR_REVIEW",
  "outcome": "REVIEW_REQUIRED",
  "workerRequestMessage": null,
  "guideReviewRequired": true,
  "guideFailureCode": "LANGUAGE_ASSISTANT_NOT_CONFIGURED",
  "caseSignals": ["REVIEW_WORKER_GUIDE"]
}
```

Server는 `guideFailureCode`를 안전한 허용 목록으로 검증하고 Task의
`renewal_execution` 메타데이터와 감사로그에 보존합니다. 이 경로에서는 근로자 안내
초안, Worker Link, SMS를 자동으로 생성하거나 발송하지 않습니다. Task는 누락정보 입력
상태로 회귀하지 않고 HR 검토 대상으로 남습니다. HR이 안전한 안내문을 작성하고 기존
승인 절차를 마친 뒤에만 Worker Link 발급과 전달 흐름을 진행합니다.

Runtime이 `languageAssistant`에 생성 결과를 함께 보낸 경우 Server는 임의 Provider 응답
전체가 아니라 아래 검토 필드만 `guide_review_draft`로 선별해 Task 실행정보와 API 응답에
보존합니다.

```json
{
  "guide_review_draft": {
    "target_language": "vi",
    "generation_status": "warning",
    "standard_korean_text": "여권 사본을 제출해 주세요.",
    "easy_korean_text": "여권을 내 주세요.",
    "translated_text": "Vui lòng nộp bản sao hộ chiếu.",
    "warning_codes": ["SEMANTIC_VALIDATION_INCONCLUSIVE"]
  }
}
```

이 값은 HR에게 보여 주는 수정 전 제안일 뿐 `document_request_draft`가 아닙니다. 검토 필요
경로에서는 `worker_message_draft_id=null`을 유지하며, HR이 제안문을 확인·수정해 기존
문서 요청 초안 API에 저장하고 승인을 끝내기 전에는 Worker Link나 SMS를 만들지 않습니다.

기존 Runtime이 신규 필드를 보내지 않으면 `guideReviewRequired=false`,
`guideFailureCode=null`로 해석하므로 정상 `ask_worker` 계약은 그대로 유지됩니다.

## Renewal 생성 문서 연결

Renewal 응답의 `generatedDocuments[]`는 다음 세 값을 문서 생성 기준으로 사용합니다.

```json
{
  "template_id": "standard_labor_contract_v6",
  "format": "hwp",
  "values": {
    "employee_name": "NGUYEN VAN AN"
  }
}
```

Server는 `scenario=generate`일 때만 각 항목을 Agent의
`POST /api/v1/documents/generate`에 `multipart/form-data`의 `payload` JSON으로 전달합니다.
응답 파일은 기존 `stored_file`에 저장하고 Task·Worker에 속한 `worker_document`와 연결합니다.
검토 전 초안이므로 `worker_document.submission_status=SUBMITTED`로 기록하고 자동으로
`VERIFIED` 처리하지 않습니다.

`values`에는 개인정보가 포함될 수 있으므로 문서 생성 요청 중에만 메모리에서 사용합니다.
Task JSON, 감사로그, 외부 API 응답에는 저장하지 않으며 다음 식별자만 반환합니다.

```json
{
  "template_id": "standard_labor_contract_v6",
  "format": "hwp",
  "status": "GENERATED",
  "stored_file_id": "...",
  "worker_document_id": "..."
}
```

현재 허용 템플릿은 Renewal 필수 초안 4종이며, 표준근로계약서는 `CONTRACT`, 나머지 연장·
체류 신청 문서는 `PERMIT`로 문서함에 분류합니다. 새 템플릿을 추가할 때는 Agent 계약과
Server 문서 종류 매핑을 함께 변경합니다.

OCR까지 활성화하려면 별도 Secret과 결과 암호화 키를 함께 설정합니다.

```dotenv
AI_OCR_ENABLED=true
AI_OCR_ENDPOINT=https://ai.example.com/internal/v1/ocr/worker-documents
AI_OCR_SERVICE_CREDENTIAL=<배포 환경 Secret>
DOCUMENT_OCR_ENABLED=true
OCR_RESULT_ENCRYPTION_KEY_BASE64=<32바이트 난수의 Base64>
OCR_RESULT_KEY_VERSION=demo-v1
```

`AI_OCR_ENABLED`만 켜거나 암호화 키 없이 `DOCUMENT_OCR_ENABLED`를 켜지 않습니다. 암호화
키는 Git에 저장하지 않고 배포 Secret으로 주입합니다. key version은 암호문과 함께 남겨
향후 KMS/Vault Adapter로 교체할 때 어떤 키로 생성했는지 추적합니다. AI #20의 Stateless
OCR 구현이 CI를 통과해 병합되고 실제 파일 smoke test까지 끝나기 전에는 두 기능 스위치를
운영에서 `false`로 유지합니다.

## 장애가 발생하면

| 상황 | 안전한 실패 코드 | 처리 방향 |
| --- | --- | --- |
| 기능 비활성화 | `RUNTIME_DISABLED` | 설정과 AI 계약 release 확인 |
| 동시 호출 한도 초과 | `BULKHEAD_FULL` | #24가 새 AiAttempt로 재시도 여부 결정 |
| 회로 차단 중 | `CIRCUIT_OPEN` | Runtime 복구 대기 |
| 전체 제한시간 초과 또는 Runtime `408` | `DEADLINE_EXCEEDED` | 자동 재시도하지 않음 |
| 서비스 인증 실패 | `AUTHENTICATION_FAILED` | 배포 Secret과 audience/scope 확인 |
| `429` | `RATE_LIMITED` | Runtime 정책 확인 후 명시적 재시도 |
| `5xx` | `RUNTIME_UNAVAILABLE` | Runtime 상태 확인 |
| 큰 응답 | `RESPONSE_TOO_LARGE` | 계약과 응답 크기 조사 |
| 잘못된 JSON | `RESPONSE_PARSING_FAILED` | contract version과 schema 조사 |
| 네트워크 오류 | `TRANSPORT_FAILURE` | DNS·TLS·네트워크 상태 확인 |

오류 메시지에는 credential, endpoint query, 응답 원문을 넣지 않습니다. #24는 안전한 실패
코드, `requestId`, version, latency만 AiAttempt 진단값으로 저장합니다.

## AI 저장소와 연결하는 순서

1. `fowoco/ai`가 `/internal/v1/analyses` OpenAPI와 JSON Schema를 versioned release로 냅니다.
2. Server의 camelCase fixture와 AI 원본 계약이 같은지 consumer contract test로 확인합니다.
3. staging에 service credential과 endpoint를 Secret으로 주입합니다.
4. 정상·`401`·`429`·`5xx`·timeout smoke test를 통과시킵니다.
5. 그 후에만 `AI_RUNTIME_ENABLED=true`를 적용합니다.

계약이 다르면 임시 필드나 호환되지 않는 JSON을 Server에 추가하지 않고, 양쪽 저장소에서
`contractVersion`을 합의한 다음 fixture를 함께 갱신합니다.
