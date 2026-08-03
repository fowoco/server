# Agent 요청 Slot 조회와 재호출

이 문서는 AI Runtime이 `CONTEXT_REQUIRED`를 반환했을 때 Server가 Worker DB 값을
안전하게 보충하고 ANALYZE를 다시 호출하는 #74 구현을 설명합니다.

## 한 줄 설명

Agent가 DB를 직접 조회하는 대신 필요한 **canonical key**만 말하고, Server가 현재
사업장과 Knowledge allow-list를 확인한 뒤 고정된 코드로 값을 찾아 줍니다.

```text
PLAN response: CONTEXT_REQUIRED
  → AiSlotResolutionTransaction
      1. company tenant context 설정
      2. detectedIntent에 해당하는 Workflow projection 조회
      3. AiRun의 requiredKnowledgeVersion과 활성 bundle version 확인
      4. requiredFieldKeys allow-list 검사
      5. 같은 company 안에서 targetDisplayName 조회
      6. 고정 switch로 Worker 값 변환
  → AiAttemptStarter (#24가 PostgreSQL 구현)
  → ANALYZE request
  → AiRuntimeClient (#56)
```

DB transaction은 Slot 조회가 끝나면 닫습니다. 외부 Runtime 응답을 기다리는 동안 DB
transaction과 connection을 붙잡지 않습니다.

## canonical key 기준

key의 원본은 `fowoco/knowledge`의 `required_slots.yaml`입니다. Server의 활성 Workflow
projection은 다음 세 집합을 구분합니다.

- `requiredSlots`: Workflow 시작에 필요한 값
- `allowedSlotKeys`: Agent candidate와 질문에서 사용할 수 있는 전체 Slot
- `resolvableSlotKeys`: Server context 조회를 요청할 수 있는 Slot

분석이 고정한 `requiredKnowledgeVersion`과 현재 활성 projection의 `bundleVersion`이 다르면
서로 다른 지식 기준을 섞지 않고 `KNOWLEDGE_VERSION_MISMATCH`로 중단합니다.

MVP Worker DB Resolver가 실제 값으로 바꿀 수 있는 key는 다음과 같습니다.

| canonical key | Server 값 |
| --- | --- |
| `worker_id` | 현재 사업장 Worker UUID |
| `stay_expiry_date` | Worker의 체류기간 만료일 |
| `contract_end_date` | Worker의 계약 종료일 |

`due_at`처럼 Knowledge에서 context 조회 가능하지만 현재 Worker DB로 계산할 수 없는 값은
`missingFieldKeys`로 반환합니다. `legal_name`처럼 활성 projection이 허용하지 않은 key는
DB column을 추측하지 않고 `FORBIDDEN_FIELD`로 거부합니다.

## 대상 근로자 확인

MVP는 한 요청에서 Worker 한 명만 처리합니다.

- 현재 `companyId` 안에서 `targetDisplayName`이 정확히 한 명이면 계속 진행합니다.
- 없으면 `TARGET_NOT_FOUND`입니다.
- 같은 사업장에 동명이인이 두 명 이상이면 `TARGET_AMBIGUOUS`입니다.
- 다른 사업장에 같은 이름이 있어도 조회 결과에 포함하지 않습니다.

오류 메시지에는 실제 이름이나 조회값을 넣지 않습니다.

## 두 번째 요청에서 보존하는 값

ANALYZE 요청은 다음을 잃어버리면 안 됩니다.

- 동일한 `requestId`
- 새로운 `attemptId`
- 원래 HR `instruction`
- 선택적 `intentHint`
- PLAN이 추출한 `extractedSlots`
- PLAN이 요청한 전체 `requestedFieldKeys`
- DB에서 찾은 값만 포함한 `workers[0].requestedFields`
- 활성 Knowledge의 `workflowConstraints`

`requestedFieldKeys`에는 DB에 값이 없던 key도 남습니다. Runtime은 전체 요청 key와 실제로
채워진 값의 차이를 보고 `NEEDS_INFO + questions`를 반환할 수 있습니다.

## Attempt와 반복 제한

`AiAttemptStarter`는 Runtime HTTP 호출 전에 호출됩니다. 현재 #74에서는 Port만 정의하고,
#24가 V12 AiAttempt table과 transaction으로 구현합니다.

- 같은 분석: `requestId` 유지
- 매 Runtime 호출: 새 `attemptId`
- 자동 DB 보충: 최대 2회
- Remote HTTP client의 투명 retry: 금지
- Agent 결과만으로 Task 생성·승인·발송: 금지

2회를 초과하면 계속 자동 호출하지 않고 HR 확인 흐름으로 넘겨야 합니다. 실제 AiRun 상태와
질문 저장은 #24와 #77에서 연결합니다.

## 검증

- `AiSlotResolutionTransactionTest`: allow-list, 누락값, 금지 key, 대상 없음·동명이인·타사 방어
- `AiSlotResolutionIntegrationTest`: 실제 JPA query가 `companyId + displayName`으로 격리되는지 확인
- `AiAnalysisContinuationServiceTest`: Attempt 기록이 HTTP 호출보다 먼저이며 PLAN 문맥이
  ANALYZE에 보존되는지 확인

이 기능은 DB migration을 추가하지 않습니다. `V12__create_ai_run.sql`은 #24가 소유합니다.
