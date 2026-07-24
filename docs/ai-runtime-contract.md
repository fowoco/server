# Server ↔ AI Runtime 계약 기반

이 문서는 `fowoco/server`가 별도 배포되는 `fowoco/ai` Runtime을 호출할 때 지켜야 하는
최소 계약과 방어 규칙을 설명합니다.

현재 단계는 **HTTP 연결 전 계약 기반**입니다. 실제 `/internal/v1/analyses` OpenAPI와
Structured Output JSON Schema의 원본은 `fowoco/ai`가 소유하며, 원본 계약이 release되면
Server의 `RemoteAiRuntimeClient`와 fixture를 그 version에 맞춰 연결합니다.

## 초보자용 한 줄 설명

AI 서버에 무엇을 보낼 수 있는지 먼저 좁혀 놓고, AI가 돌려준 값도 그대로 믿지 않고 다시
검사하는 안전문입니다.

```text
AiRunWorker (#24, 후속)
  → ValidatingAiRuntimeClient
      1. 요청 개인정보·허용 범위 검사
      2. AiRuntimeClient transport를 정확히 한 번 호출
      3. 응답 ID·version·worker·workflow·slot 재검사
  → FakeAiRuntimeClient (test)
  → RemoteAiRuntimeClient (#8 후속)
      → POST /internal/v1/analyses (fowoco/ai)
```

`AiRuntimeClient`는 OpenAI, Gemini, Anthropic 같은 Provider를 직접 호출하지 않습니다.
Prompt, Agent Pipeline, Provider retry와 모델 선택은 `fowoco/ai` 책임입니다.

## 요청 계약

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "attemptId": "20000000-0000-0000-0000-000000000001",
  "contractVersion": "1.0.0",
  "requiredKnowledgeVersion": "0.2.0",
  "deadlineMs": 10000,
  "maskedInput": {
    "maskedInstruction": "workerRef 30000000-0000-0000-0000-000000000001의 체류연장 준비",
    "workers": [
      {
        "workerRef": "30000000-0000-0000-0000-000000000001",
        "preferredLanguage": "vi",
        "workStatus": "ACTIVE",
        "stayExpiryDate": "2026-12-31"
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
- `contractVersion`: 양쪽이 같은 JSON 계약을 사용하는지 확인합니다.
- `requiredKnowledgeVersion`: Server와 Runtime이 같은 Workflow release를 사용하게 합니다.
- `deadlineMs`: 이번 시도 전체에서 남은 실행 시간입니다.
- `maskedInstruction`: 이름과 식별번호를 `workerRef`로 바꾼 자연어입니다.
- `workflowConstraints`: Knowledge projection에서 가져온 Workflow와 slot allow-list입니다.

근로자 Context에는 여권번호, 외국인등록번호, 전화번호, 계좌번호, 법적 실명, 원본 문서와
Worker Link token을 추가하지 않습니다.

## 응답 계약

```json
{
  "requestId": "10000000-0000-0000-0000-000000000001",
  "outcome": "REVIEW_REQUIRED",
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

`NEEDS_INFO`와 `REVIEW_REQUIRED`는 정상 분석 결과입니다. 이 값은 AiRun의 기술적
`FAILED` 상태와 섞지 않습니다. Candidate는 Task도 승인도 아니며, #24에서 HR이 채택한
후에만 Server Task command로 전달됩니다.

## Server가 거부하는 응답

- 요청과 다른 `requestId`
- 요청과 다른 contract 또는 Workflow Catalog version
- 요청에 없던 `workerRef`나 `workflowId`
- Workflow가 허용하지 않은 slot
- 0 미만 또는 1 초과 confidence
- 중복 candidate reference와 잘못된 outcome 구조
- 여권번호·외국인등록번호·전화번호·계좌번호·Bearer Token·Secret이 섞인 값

거부 예외에는 발견한 원문을 넣지 않습니다. 앞으로 #24 AiAttempt에는
`AiRuntimeFailureCode`와 `requestId` 같은 안전한 진단값만 저장합니다.

## 테스트와 실제 구현의 차이

- `FakeAiRuntimeClient`: `src/test`에만 있으며 응답이나 예외를 순서대로 예약합니다.
- `ValidatingAiRuntimeClient`: transport 앞뒤에서 같은 방어 검증을 수행합니다.
- `RemoteAiRuntimeClient`: 아직 없습니다. AI 원본 계약 release 후 추가합니다.

실제 HTTP 연결 PR에서는 다음을 추가로 검증합니다.

1. `Service-Authorization`, `X-Request-Id`, `traceparent` 전달
2. 알 수 없는 JSON field와 body size 제한
3. connect/read/overall deadline
4. circuit breaker와 concurrency bulkhead
5. HTTP·parsing·contract 오류의 안정적인 분류
6. contract fixture와 WireMock 통합 테스트

Remote Client는 자동 HTTP retry를 하지 않습니다. 다시 호출하려면 #24가 먼저 새로운
AiAttempt를 DB에 기록해야 합니다.
