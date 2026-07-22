# ADR-0001: 저장소·모듈 경계와 계약 소유권

- Status: Proposed
- Date: 2026-07-22
- Deciders: FOWOCO Server Team
- Related Issue: [#23](https://github.com/fowoco/server/issues/23)
- Supersedes: None

## Context

FOWOCO는 Client, Server, AI Runtime, Knowledge를 별도 저장소에서 개발합니다. 경계가 없으면 다음 문제가 생깁니다.

- Server와 AI가 Prompt·Provider 연동을 각각 구현합니다.
- Server와 Knowledge가 Workflow 의미를 서로 다른 enum과 YAML로 관리합니다.
- AI가 HR 권한·승인·업무 상태까지 판단하게 됩니다.
- 계약 파일을 저장소마다 복사해 어느 파일이 원본인지 알 수 없게 됩니다.
- 한 기능이 다른 서비스의 DB나 내부 구현에 직접 의존합니다.

MVP는 빠르게 개발해야 하지만, 개인정보와 승인 절차를 다루므로 책임을 합치는 것으로 속도를 내지 않습니다. Server는 AI 모델이 아니라 **통제된 HR Workflow의 운영 서버**입니다.

## Decision

### 1. 런타임 호출 구조

```text
Client
  → Server
    → PostgreSQL / FileStorage Port
    → AiRuntimeClient → AI Runtime → Model Provider

Versioned Knowledge Bundle
  → AI Runtime
  → Server의 read-only Workflow projection
```

Client는 Server만 호출합니다. Server는 Provider를 직접 호출하지 않고 AI Runtime의 versioned Internal API만 소비합니다. AI Runtime과 Knowledge는 Server 운영 DB에 접근하지 않습니다.

### 2. 저장소 책임

| 저장소 | 소유하는 책임 | 소유하지 않는 책임 |
| --- | --- | --- |
| `server` | 인증·tenant, Worker·Document metadata, Task 상태, 승인·증빙·감사, Worker Link, AiRun 영속 상태, 개인정보 최소화, Client/Public API | Prompt, 모델 선택, Provider SDK, Knowledge 원문·RAG index |
| `ai` | Agent Pipeline, Prompt, model routing, Provider 호출, Structured Output 생성, provider-attempt retry | HR 권한, Task 승인, Worker Link, Server DB 상태 변경 |
| `knowledge` | Intent·Domain·Slot, Context Pack, Workflow Catalog, 용어의 검증·immutable version release | 사용자 요청 처리, Provider 호출, 사용자별 개인정보, 운영 DB 변경 |
| `client` | HR 입력·검토·승인 UX와 Worker Link 화면 | 권한 최종 판정, 상태 전이 우회, AI Runtime 직접 호출 |
| `infra` | 네트워크, Secret, 배포, 관측 기반 | 각 서비스의 Domain 정책 |

### 3. 계약의 원본

| 계약 | Owner | Consumer |
| --- | --- | --- |
| Server Authenticated/Public OpenAPI | `server` | `client` |
| `/internal/v1/analyses` OpenAPI | `ai` | `server` |
| AI Structured Output JSON Schema | `ai` | `server` |
| Intent·Workflow Catalog·Context Pack | `knowledge` | `ai`, 필요한 `server` projection |
| Task·AiRun 상태와 Command | `server` | `client`, audit |
| Domain Event envelope와 내구성 규칙 | `server` | Server 내부 handler |

계약 변경은 다음 순서를 따릅니다.

```text
Owner 저장소 PR
→ Contract test
→ Versioned release
→ Consumer pin 갱신
→ 통합 Smoke test
→ Wiki·Notion mirror 동기화
```

Consumer 저장소에 원본 Schema를 복사해 독립적으로 수정하지 않습니다. 테스트 fixture가 필요하면 원본 release/version에서 생성하고 그 version을 기록합니다.

### 4. Knowledge Catalog와 Server Task Workflow의 차이

두 저장소에서 모두 `Workflow`라는 용어를 쓰지만 역할은 다릅니다.

| 구분 | Knowledge Workflow Catalog | Server Task Workflow |
| --- | --- | --- |
| 성격 | immutable versioned 설명서 | 사용자별 mutable 운영 상태 |
| 내용 | Task 유형, required slot, checklist template, evidence requirement | 현재 상태, 권한, 승인 revision, 응답·제출·증빙 |
| 변경 주체 | `knowledge` release | 인증된 Server Command |
| 개인정보 | 포함하지 않음 | 최소화된 업무정보를 tenant별 저장 |

Server는 Catalog의 read-only projection을 읽지만 Catalog YAML을 독자적으로 수정하지 않습니다. Knowledge는 Server의 Task 상태를 읽거나 변경하지 않습니다. Server와 AI Runtime은 같은 `workflow_catalog_version`을 사용합니다.

### 5. Server는 modular monolith로 시작

MVP에서는 서비스별 DB나 Kafka를 도입하지 않고 하나의 Spring Boot 애플리케이션과 PostgreSQL로 운영합니다. 기능은 다음 논리 모듈로 분리합니다.

| 모듈 | 책임 | 금지 사항 |
| --- | --- | --- |
| `auth`, `company` | JWT, role, ActorContext, Company 경계 | AI Runtime 내부 구현 참조 |
| `worker`, `document`, `file` | 최소 개인정보, 문서 metadata, FileStorage Port | Task 상태 직접 변경 |
| `task` | Task, checklist, command, guard, optimistic lock | Provider SDK·Prompt 참조 |
| `approval`, `audit` | 승인 snapshot, evidence, append-only audit | 원본 token·민감 원문 저장 |
| `workerlink` | token 발급·만료·회전과 public action | HR 내부 메모 노출 |
| `airun` | durable run, candidate snapshot, idempotency, server retry | Prompt·모델·Provider 선택 |
| `aiintegration` | `AiRuntimeClient`, S2S auth, timeout, 응답 재검증 | OpenAI·Gemini·LM Studio client |
| `reliability` | outbox와 publication recovery | MVP에서 Kafka 강제 도입 |
| `common`, `health` | 안정적인 기술 공통 요소와 운영 상태 | Feature별 업무 규칙·거대한 공통 enum |

새 기능은 다음 feature-first 구조를 기본으로 합니다.

```text
com.fowoco.server.<feature>/
  api/             HTTP DTO와 Controller
  application/     Use case, Command, Port, transaction orchestration
  domain/          Aggregate, Value Object, 상태 전이와 불변식
  infrastructure/  JPA, 외부 Client, Storage 구현
```

작은 기능은 빈 디렉터리를 미리 만들지 않습니다. 코드가 생길 때 위 역할에 맞춰 추가합니다.

### 6. 모듈 의존 규칙

- `api`는 `application`을 호출하며 Repository와 외부 Client를 직접 호출하지 않습니다.
- `application`은 자체 `domain`과 명시적인 Port를 사용합니다.
- `infrastructure`는 Port를 구현하지만 Domain 상태 전이 규칙을 복제하지 않습니다.
- `domain`은 plain Java로 유지하고 Spring MVC·JPA·외부 Client에 의존하지 않습니다.
- 다른 모듈의 JPA Entity와 Repository를 직접 사용하지 않습니다.
- 다른 모듈과는 ID, immutable snapshot, application Port 또는 Domain Event로 연결합니다.
- `common`으로 Feature 코드를 옮겨 순환 의존성을 숨기지 않습니다.
- AI 결과는 candidate일 뿐이며 `task`와 `approval`의 Command를 우회할 수 없습니다.

즉시 적용 가능한 코드 리뷰 기준은 다음과 같습니다.

- `aiintegration` 이외의 패키지에서 AI Runtime HTTP client를 만들지 않습니다.
- 어느 패키지에서도 Provider SDK와 Prompt Builder를 추가하지 않습니다.
- AI·Knowledge·Client가 Server Entity/Repository에 접근하는 코드를 만들지 않습니다.
- `task` 이외의 모듈에서 Task 상태를 직접 대입하지 않습니다.
- Feature가 다른 Feature의 `infrastructure` 패키지를 import하지 않습니다.

Feature 코드가 추가되면 이 규칙을 ArchUnit 또는 Spring Modulith 검증으로 자동화합니다. 도구 선택 자체는 이 ADR의 범위가 아니며, 자동화 전에는 PR checklist를 차단 기준으로 사용합니다.

### 7. Flyway migration과 병합 순서

DB Schema 변경은 코드 branch와 별도로 재정렬할 수 없으므로 두 개발자가 다음 규칙을 지킵니다.

- `V1__baseline.sql`은 이미 적용된 immutable migration으로 간주하고 수정하지 않습니다.
- 최신 `main`의 다음 번호를 Issue와 PR 본문에서 예약합니다.
- 초기 예약은 `V2` Auth·Company(#4), `V3` Worker·Document metadata(#5) 순서입니다.
- 선행 번호가 병합되기 전에 후행 migration PR을 Ready 또는 merge하지 않습니다.
- `main`에 병합되어 공유 환경에 적용된 migration은 수정·삭제·번호 변경하지 않습니다.
- PR 검토 중인 migration은 예약 번호와 공유 환경 적용 여부를 확인한 뒤 수정할 수 있습니다. 이미 공유 환경에 적용했다면 새 migration으로 교정합니다.
- 같은 번호를 두 branch에서 사용하지 않으며 migration은 가능한 한 PR 하나에 하나만 포함합니다.
- 하나의 migration은 소유 Issue의 aggregate만 생성합니다. 후속 Feature의 테이블을 편의를 위해 미리 만들거나 여러 모듈의 책임을 범용 테이블에 섞지 않습니다.
- PostgreSQL migration test는 최신 번호를 하드코딩하지 않고 validation 성공과 pending migration 없음으로 검증합니다.

### 8. Server의 AI 경계

Server에 둘 수 있는 코드는 다음과 같습니다.

- `AiRuntimeClient` Port
- 테스트/local용 `FakeAiRuntimeClient`
- 통합 환경용 `RemoteAiRuntimeClient`
- S2S 인증, timeout, circuit breaker, body size 제한
- 전송 직전 PII allow-list와 redaction
- 응답 JSON Schema, 날짜·금액·문서명·대상자 보존 여부 재검증
- AiRun, candidate snapshot, version metadata, error와 latency 저장

Server에 두지 않는 코드는 다음과 같습니다.

- `OpenAiClient`, `GeminiClient`, `LmStudioClient`
- Prompt template과 Prompt 조립기
- model routing, sampling parameter, provider fallback
- embedding과 RAG index 생성
- 모델 원문을 Task candidate로 변환하는 Agent Pipeline

## Consequences

### Positive

- Provider와 모델을 바꿔도 HR Workflow와 Client API를 유지할 수 있습니다.
- AI가 승인과 발송을 우회하지 못합니다.
- 계약의 원본과 변경 순서가 명확해집니다.
- 하나의 애플리케이션으로 빠르게 개발하면서도 모듈 분리가 가능합니다.

### Negative

- 저장소 간 contract version과 compatibility를 관리해야 합니다.
- AI Runtime이 준비되지 않은 로컬·테스트 환경에는 Fake가 필요합니다.
- 초기에는 리뷰로 의존 규칙을 확인하고 이후 자동화 작업이 필요합니다.
- Plain Java Domain과 JPA persistence model 사이에 mapper가 필요해 초기 코드가 조금 늘어납니다.

## Alternatives Considered

### Server에서 Provider를 직접 호출

초기 호출 코드는 짧아지지만 Prompt·Provider·Workflow 책임이 Server에 섞이고 자체 AI Runtime으로 교체하기 어려워 선택하지 않습니다.

### 모든 저장소가 공통 DTO 파일을 복사

각 저장소가 독립적으로 수정하면 같은 이름의 다른 Schema가 생기므로 선택하지 않습니다. Owner가 versioned contract를 배포하고 Consumer가 pin합니다.

### AI Runtime이 Server DB를 직접 조회·변경

호출 수는 줄지만 tenant, 승인, audit 경계를 우회하고 장애 범위가 커지므로 금지합니다.

### 지금 바로 Microservice와 Kafka 도입

MVP 규모에 비해 배포·운영 복잡도가 크므로 선택하지 않습니다. 먼저 modular monolith와 DB-backed outbox로 경계를 증명합니다.

## 용어 도움말

| 용어 | 뜻 |
| --- | --- |
| Port | Domain/Application이 필요한 기능을 interface로 표현하고 외부 구현과 분리하는 경계 |
| Projection | 다른 원본에서 Server가 읽는 데 필요한 field만 만든 read-only 표현 |
| Immutable bundle | 배포 후 내용을 덮어쓰지 않고 새 version으로만 변경하는 지식 묶음 |
| Modular monolith | 하나의 애플리케이션·DB로 배포하지만 코드 책임과 의존성을 모듈로 분리한 구조 |
| Contract pin | Consumer가 사용하기로 검증한 계약 version을 명시적으로 고정하는 것 |
