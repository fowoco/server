# Server ↔ AI Runtime 계약 기반

이 문서는 `fowoco/server`가 별도 배포되는 `fowoco/ai` Runtime을 호출할 때 지켜야 하는
최소 계약과 방어 규칙을 설명합니다.

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
빠른 선택 태그는 `intentHint`에 넣지만 참고 정보일 뿐이며, 최종 분류 결과는 Runtime이
`detectedIntent`로 반환합니다. 이 단계에는 Worker UUID나 DB 조회값을 넣지 않습니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "attemptId": "20000000-0000-0000-0000-000000000001",
  "phase": "PLAN",
  "contractVersion": "1.0.0",
  "requiredKnowledgeVersion": "0.2.0",
  "deadlineMs": 10000,
  "analysisInput": {
    "instruction": "응웬반안 체류연장 준비해줘",
    "intentHint": "EXPIRY_RENEWAL",
    "extractedSlots": {},
    "requestedFieldKeys": [],
    "workers": [],
    "workflowConstraints": []
  }
}
```

Runtime이 DB 정보를 더 필요로 하면 성공 응답으로 `CONTEXT_REQUIRED`를 반환합니다.
Agent는 SQL을 만들거나 DB를 직접 조회하지 않고, canonical field key만 요청합니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "outcome": "CONTEXT_REQUIRED",
  "contextRequirement": {
    "detectedIntent": "EXPIRY_RENEWAL",
    "confidence": 0.94,
    "targetDisplayName": "응웬반안",
    "extractedSlots": {},
    "requiredFieldKeys": [
      "legal_name",
      "stay_expiry_date"
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
    "contextPackVersion": "context-0.2.0",
    "workflowCatalogVersion": "0.2.0",
    "contractVersion": "1.0.0"
  },
  "providerAttemptCount": 1,
  "latencyMs": 120
}
```

## ANALYZE 요청 계약

#74가 `targetDisplayName`을 현재 사업장 안에서 한 명의 Worker로 찾고, 허용된
`requiredFieldKeys`만 Repository로 조회합니다. 그 결과를 넣어 새로운 `attemptId`로
ANALYZE를 호출합니다. MVP에서는 한 요청에 Worker 한 명만 허용합니다.

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "attemptId": "20000000-0000-0000-0000-000000000002",
  "phase": "ANALYZE",
  "contractVersion": "1.0.0",
  "requiredKnowledgeVersion": "0.2.0",
  "deadlineMs": 10000,
  "analysisInput": {
    "instruction": "응웬반안 체류연장 준비해줘",
    "intentHint": "EXPIRY_RENEWAL",
    "extractedSlots": {},
    "requestedFieldKeys": [
      "legal_name",
      "stay_expiry_date"
    ],
    "workers": [
      {
        "workerRef": "30000000-0000-0000-0000-000000000001",
        "displayName": "응웬반안",
        "nationalityCode": "VN",
        "preferredLanguage": "vi",
        "workStatus": "ACTIVE",
        "stayExpiryDate": "2026-12-31",
        "contractStartDate": "2026-01-01",
        "contractEndDate": "2026-12-31",
        "requestedFields": {
          "legal_name": "NGUYEN VAN AN",
          "stay_expiry_date": "2026-12-31"
        }
      }
    ],
    "workflowConstraints": [
      {
        "workflowId": "EXPIRY_RENEWAL",
        "allowedSlotKeys": [
          "stay_expiry_date",
          "contract_end_date",
          "monthly_wage"
        ]
      }
    ]
  }
}
```

- `requestId`: Server 요청과 Runtime 응답을 같은 실행으로 연결합니다.
- `attemptId`: 한 번의 `AiRuntimeClient.analyze` 호출과 정확히 하나로 대응합니다.
- `phase`: 발화문을 해석하는 `PLAN`과 Server 보유정보로 결과를 만드는 `ANALYZE`를 구분합니다.
- `contractVersion`: 양쪽이 같은 JSON 계약을 사용하는지 확인합니다.
- `requiredKnowledgeVersion`: Server와 Runtime이 같은 Workflow release를 사용하게 합니다.
- `deadlineMs`: 이번 시도 전체에서 남은 실행 시간입니다.
- `instruction`: HR이 입력한 원문입니다. 현재 데모에서는 가상 근로자 데이터만 사용합니다.
- `intentHint`: 화면 빠른 선택에서 온 선택값입니다. 없을 수 있으며 강제 Intent가 아닙니다.
- `extractedSlots`: PLAN에서 Agent가 발화문으로부터 추출했던 값을 ANALYZE에도 보존합니다.
- `requestedFieldKeys`: Agent가 PLAN에서 요청했던 전체 key입니다. DB에 값이 없어도 목록에는 남습니다.
- `requestedFields`: Agent가 요구한 field의 원본값입니다. Server가 가진 값만 넣습니다.
- `workflowConstraints`: Knowledge projection에서 가져온 Workflow와 slot allow-list입니다.

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
      "workflowId": "EXPIRY_RENEWAL",
      "extractedSlots": {
        "stay_expiry_date": "2026-12-31"
      },
      "missingSlots": [
        "contract_end_date",
        "monthly_wage"
      ],
      "confidence": 0.92
    }
  ],
  "validationErrors": [],
  "versions": {
    "agentVersion": "agent-1.0.0",
    "modelProvider": "openai",
    "modelName": "gpt-5-mini",
    "modelVersion": "2026-07-01",
    "promptVersion": "prompt-3",
    "contextPackVersion": "context-0.2.0",
    "workflowCatalogVersion": "0.2.0",
    "contractVersion": "1.0.0"
  },
  "providerAttemptCount": 1,
  "latencyMs": 245
}
```

`CONTEXT_REQUIRED`, `NEEDS_INFO`, `REVIEW_REQUIRED`는 모두 정상 분석 결과이며 AiRun의
기술적 `FAILED` 상태와 섞지 않습니다.

- `CONTEXT_REQUIRED`: Server DB에서 조회할 canonical field key가 있습니다.
- `NEEDS_INFO`: DB로 채울 수 없어 HR에게 보여 줄 `questions`가 있습니다.
- `REVIEW_REQUIRED`: 검토 가능한 `candidates`가 있습니다.

Candidate는 Task도 승인도 아니며, #24에서 HR이 채택한 후에만 Server Task command로
전달됩니다.

## Server가 거부하는 응답

- 요청과 다른 `requestId`
- 요청과 다른 contract 또는 Workflow Catalog version
- 요청에 없던 `workerRef`나 `workflowId`
- Workflow가 허용하지 않은 slot
- 0 미만 또는 1 초과 confidence
- 중복 candidate reference와 잘못된 outcome 구조
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
2. 문서와 같은 camelCase 요청 JSON 및 `PLAN → CONTEXT_REQUIRED → ANALYZE` 구조 사용
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
| `AI_RUNTIME_MAX_CONCURRENT_CALLS` | `8` | Server 한 인스턴스가 동시에 보내는 최대 호출 수 |
| `AI_RUNTIME_CIRCUIT_BREAKER_FAILURE_THRESHOLD` | `5` | 연속 장애 후 호출을 잠시 막는 기준 |
| `AI_RUNTIME_CIRCUIT_BREAKER_OPEN_DURATION` | `30s` | 차단 후 시험 호출까지 기다리는 시간 |

요청의 `deadlineMs`와 `AI_RUNTIME_OVERALL_TIMEOUT` 중 더 짧은 값을 사용합니다. 따라서
상위 AiRun이 허용한 시간보다 오래 기다리지 않습니다.

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
