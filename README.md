# FOWOCO Server

<p align="center">
  <a href="https://github.com/fowoco/server/actions/workflows/ci.yml"><img alt="Server CI" src="https://github.com/fowoco/server/actions/workflows/ci.yml/badge.svg?branch=main"></a>
  <a href="https://github.com/fowoco/server/actions/workflows/database-docs.yml"><img alt="Server Documentation" src="https://github.com/fowoco/server/actions/workflows/database-docs.yml/badge.svg?branch=main"></a>
</p>

E-9 외국인근로자를 고용한 사업장의 체류·계약·서류·신고 업무를 안전한
HR Workflow로 운영하는 Spring Boot 백엔드입니다.

FOWOCO는 단순 번역 서비스가 아닙니다. 해야 할 일을 업무카드로 구조화하고,
필수정보·승인·증빙·다음 행동을 담당자가 놓치지 않도록 돕습니다.

> AI는 판단자가 아니라 보조자입니다. AI 결과는 인증, 사업장 권한, 상태 전이,
> HR 승인과 감사로그 안에서만 사용합니다.

## 프로젝트 한눈에 보기

FOWOCO Server는 세 명의 백엔드 개발자가 기능 경계를 나누되, 공통 계약과 PR을
서로 검토하며 만든 **modular monolith**입니다. 인증부터 근로자·문서, 업무카드,
AI 실행, 승인, 근로자 링크, 알림과 장애 복구까지 하나의 PostgreSQL 기반 업무
흐름으로 연결합니다. 핵심은 API 개수가 아니라 **AI의 제안을 HR이 검토하고,
근로자 응답을 다시 공식 업무와 다음 행동으로 회수하는 전체 흐름**입니다.

| 구분 | 현재 상태 |
| --- | --- |
| 핵심 업무 API | Auth·Worker·Document·Task·Approval·Worker Link·Case·Dashboard·Notification 구현 |
| AI 연동 | PLAN에서 대표 Intent·Workflow를 한 번 결정하고, 허용 Slot을 보충한 뒤 같은 결정을 ANALYZE에 재사용하는 AiRun·SSE 흐름 구현 |
| 문서 처리 | 파일 저장·다운로드, HWP/HWPX 검증·생성 결과 연계, OCR 실행·HR 검토 구현 |
| 근로자 협업 | 만료형 보안 링크 발급, 모바일 안내·응답·서류 제출, HR 공식 서류 채택과 Task 재개, 퇴사 근로자 안전 보관 구현 |
| 알림 | 업무 Domain Event와 Outbox를 이용한 알림 생성, 읽음 상태, 마감 임박 배치 구현 |
| 운영 기반 | Flyway, PostgreSQL 16, RLS, Transactional Outbox, 감사로그, Micrometer·Prometheus, Docker·Kubernetes·HTTPS 배포와 제품 E2E 검증 |

## 왜 이 기술과 구조를 선택했는가

> 기술의 인지도보다 **세 명이 만드는 배포 가능한 MVP**, **사업장 데이터 격리**,
> **AI 결과의 통제**, **장애 후 업무 복구**에 적합한지를 선택 기준으로 삼았습니다.

| 선택 | 해결해야 했던 서비스 문제 | 판단과 적용 |
| --- | --- | --- |
| Spring Boot modular monolith | 인증·Task·승인·감사로그가 하나의 업무 트랜잭션으로 연결됨 | Microservices의 분산 트랜잭션·배포 부담을 먼저 만들지 않고 데이터 일관성과 개발 속도를 확보했습니다. 대신 패키지와 Port로 경계를 나눠 AI 실행·파일 처리처럼 독립성이 커지는 영역부터 추후 분리할 수 있게 했습니다. |
| PostgreSQL·Flyway·RLS | 사업장별 데이터와 상태 전이·승인 이력을 관계와 트랜잭션으로 보존해야 함 | PostgreSQL을 업무 상태의 단일 기준으로 두고, Flyway로 재현 가능한 변경 이력을 관리했습니다. 애플리케이션의 `company_id` 조건에만 의존하지 않고 복합 FK와 RLS로 DB 격리를 보강했습니다. |
| Spring Security·JWT·RBAC | HR 사용자 역할과 사업장 권한을 모든 API에서 일관되게 검사해야 함 | Access Token은 stateless JWT로 검증하고 `ActorContext`에서 사용자·역할·사업장을 결정했습니다. 계정이 없는 근로자는 별도의 만료형 Worker Link로 제한된 기능만 사용하게 했습니다. |
| Server–AI HTTP 경계와 allow-list Resolver | AI가 업무 DB를 직접 조회하면 권한 우회와 개인정보 과다 전달 위험이 있음 | AI Runtime의 DB 직접 접근을 허용하지 않고, PLAN에서 요청한 허용 field만 Server가 tenant 범위로 조회해 ANALYZE에 보충하도록 설계했습니다. PLAN의 대표 Intent·Workflow를 저장·재사용해 같은 발화문을 두 번 분류할 때 생길 수 있는 결과 불일치와 지연도 줄였습니다. 판단·권한·영속 상태는 Server가 소유합니다. |
| REST + AiRun + SSE | AI 실행은 오래 걸리지만 Client가 보내야 하는 실시간 메시지는 없음 | 요청과 결과는 재조회 가능한 AiRun resource로 저장하고, 단방향 상태 알림은 WebSocket보다 단순한 SSE를 사용했습니다. 연결이 끊겨도 DB 상태를 다시 조회할 수 있게 했습니다. |
| PostgreSQL Transactional Outbox | DB 변경 성공 후 알림·OCR 같은 후속 실행이 실패하면 업무가 유실될 수 있음 | 별도 Broker를 먼저 운영하는 대신 업무 변경과 Event를 같은 DB 트랜잭션에 저장했습니다. lease·backoff·멱등성·수동 재처리로 장애 후에도 처리 상태가 수렴하도록 했습니다. |
| Domain Event 알림 + 예약 배치 | 승인·근로자 제출처럼 즉시 알려야 하는 사건과 마감 임박처럼 시간 기준으로 찾는 사건이 함께 존재함 | 업무 코드가 알림 테이블을 직접 조작하지 않고 Domain Event를 발행하게 했습니다. Outbox 소비 이력으로 중복 생성을 막고, 마감 임박 알림만 회사별 tenant context를 적용한 예약 배치로 분리했습니다. |
| Micrometer + Prometheus | 데모에서도 AI가 느린지, DB 보충이 느린지, 어느 단계가 실패했는지 수치로 설명해야 함 | PLAN·Slot 조회·ANALYZE·결과 저장·Renewal 단계를 Timer와 Counter로 분리했습니다. 원문과 개인정보를 label에 넣지 않으면서 p50·p95·Outcome·오류 코드·Outbox backlog를 조회할 수 있게 했습니다. |

### Backend Team

| 개발자 | 주로 완성한 영역 |
| --- | --- |
| 최현준 [`@hywznn`](https://github.com/hywznn) | Auth·Task·Approval/Audit·AI Integration·AiRun·Case·Outbox·OCR·통합 흐름 |
| 김채린 [`@chaeliki`](https://github.com/chaeliki) | Worker·Document/File·Worker Link·Dashboard·Notification |
| 김재성 [`@krestar`](https://github.com/krestar) | PostgreSQL·RLS·Settings·Demo Seed·DB 운영 안전성 |
| 함께 | API 계약, Flyway 순서 조율, 상호 PR Review, 배포·E2E 준비 |

위 표는 프로젝트 기여를 이해하기 위한 요약입니다. 현재 담당자와 완료 조건은
[GitHub Issues](https://github.com/fowoco/server/issues)의 Assignee와
[Server Roadmap](https://github.com/orgs/fowoco/projects/3)을 기준으로 확인합니다.

## Server가 담당하는 일

- 사업장 사용자 인증과 `ADMIN`·`HR`·`VIEWER` 권한
- `company_id`를 기준으로 한 사업장 데이터 격리
- 근로자 기본정보와 서류 메타데이터 관리
- 체류 만료 경과 확인과 퇴사 근로자의 삭제 없는 안전 보관·업무 차단
- CSV/XLSX 근로자 명단 가져오기와 OCR 검토
- 업무카드·체크리스트·상태 전이 관리
- HR 승인·반려·외부 제출·증빙·완료와 감사로그
- 만료되는 근로자 보안 링크, 모바일 응답·서류 제출과 HR 공식 서류 채택
- AI Runtime PLAN·ANALYZE 요청, Slot 보충, 응답 검증과 영속 실행 이력
- Today Dashboard·이벤트 기반 알림·읽음 상태·사업장 설정 조회
- 실패해도 유실되지 않는 후속 이벤트 처리

Provider SDK, Prompt와 모델 라우팅은 Server에 구현하지 않습니다.

| 저장소 | 책임 |
| --- | --- |
| `server` | 인증·권한·Worker·Task·승인·감사·링크·영속 상태 |
| `ai` | Prompt, Agent Pipeline, 모델·Provider 호출 |
| `knowledge` | Intent·Slot·Workflow Catalog·공식 근거·평가 데이터 |
| `client` | HR·근로자 화면과 사용자 상호작용 |
| `infra` | 통합 배포 환경, 네트워크, Secret과 관측 인프라 |

상세 소유권과 금지 의존성은
[ADR-0001](docs/adr/0001-repository-and-module-boundaries.md)을 따릅니다.

## 대표 흐름

```text
HR 로그인
→ 근로자·서류 확인
→ 자연어 또는 D-day 이벤트 분석
→ 업무카드 후보 검토·확정
→ 필요정보와 문서 초안 확인
→ HR 승인
→ 근로자 보안 링크 발급·모바일 안내
→ 근로자 응답·서류 제출
→ HR 제출자료 확인·공식 서류 채택
→ OCR 실행·HR 결과 검토
→ 승인된 OCR Context로 기존 Task 재개·문서 초안 생성
→ 외부 제출·처리결과 기록
→ 완료·감사로그
→ 퇴사·업무 종료 확인 후 운영 목록에서 안전 보관
```

대표 입력:

> 응웬반A가 3년 만료 예정이야. 재계약하고 체류연장 준비해줘.

목표 결과는 재계약·취업활동기간 연장·체류기간 연장 Workflow를 분리해 만들고,
누락정보를 담당자에게 질문하는 것입니다. FOWOCO가 기관에 자동 로그인하거나
신청서를 대신 제출하지는 않습니다.

### AI 분석 흐름

```text
Client가 HR 원문 전송
→ Server가 AiRun 생성
→ AI PLAN이 대표 Intent·Workflow 1회 결정
→ Server가 결정을 검증·저장하고 허용된 업무정보만 조회
→ AI ANALYZE가 저장된 결정을 재사용해 Candidate 생성
→ Server가 Workflow 일치 여부를 다시 검증
→ HR이 Candidate를 검토한 뒤 Task로 채택
```

PLAN과 ANALYZE에서 같은 문장을 각각 분류하면 실행 중 Workflow가 달라질 수 있고
모델 호출 시간도 중복됩니다. 그래서 Server는 PLAN 결정을 실행 이력에 남기고,
ANALYZE에는 `plannedIntent`와 `plannedWorkflowId`를 전달합니다. 확률을 제공하지 않는
모델의 `confidence`는 `null`을 허용하며, BERT 라우팅 점수를 A.X의 확률처럼 사용하지
않습니다. 상세 요청·응답과 검증 기준은
[AI Runtime 계약](docs/ai-runtime-contract.md)을 확인합니다.

### 근로자 모바일 응답이 업무로 돌아오는 흐름

```text
HR이 승인된 Task의 Worker Link 발급
→ Task: APPROVED → WAITING_WORKER
→ 근로자가 로그인 없이 안내 확인·질문·파일 제출
→ Server가 WorkerResponse와 StoredFile로 보관
→ HR이 제출 파일의 이름·형식·크기·서류 유형 확인
→ HR이 채택한 파일만 WorkerDocument(SUBMITTED)로 등록
→ 여권·외국인등록증 채택 이벤트가 Outbox를 통해 OCR 실행 요청
→ HR이 OCR 추출값을 원본과 대조해 수정·승인
→ 승인된 OCR 값을 Context에 병합해 기존 Renewal Task 재실행
→ 모든 필수정보가 갖춰지면 HWP/HWPX 초안을 저장하고 HR 검토로 이동
```

근로자의 제출만으로 개인정보와 공식 서류 상태를 자동 확정하지 않습니다.
`SUBMITTED`와 `VERIFIED`를 분리하고, HR이 확인한 파일만 기존 업무와 연결합니다.
같은 파일을 다시 채택해도 문서가 중복 생성되지 않으며, 링크 발급·상태 전이·채택은
감사로그와 Task 전이 이력으로 추적합니다. OCR 결과도 Worker 원본 개인정보를 자동으로
덮어쓰지 않고, HR이 승인한 결과만 Renewal Context에서 사용합니다. 생성된 문서는
자동 발송하지 않으며 다시 HR 검토를 거칩니다.

Language Assistant가 근로자 안내를 안전하게 만들지 못한 경우에도 임시 문장을 발송하지
않습니다. Server는 생성된 한국어·쉬운 한국어·번역문을 `guide_review_draft`로 보존해
HR이 검토할 수 있게 하고, 실패 코드도 함께 기록합니다. 이 제안문은 공식 안내 초안으로
자동 확정되지 않으며 담당자가 수정·승인한 뒤에만 Worker Link와 SMS 전달을 허용합니다.

### 업무 이벤트가 알림으로 이어지는 흐름

```text
Task·Approval·Worker Link 업무 변경
→ Domain Event 발행
→ 같은 DB 트랜잭션의 Outbox에 저장
→ Event handler가 사용자 알림 생성
→ Client가 알림 목록·미읽음 수 조회
→ 사용자가 읽음 처리
```

- Agent 후보 채택 후 생성된 업무
- 승인 요청 도착
- 근로자 문서 제출 완료
- 체크리스트 변경으로 발생한 문서 보완 필요
- 7일 이내 마감 임박 업무

즉시 발생하는 네 가지 알림은 Event로 처리하고, 현재 시각을 기준으로 찾아야 하는
마감 임박 알림은 매일 실행되는 배치로 처리합니다. Event 재처리 시에는 기존
`EventConsumption` 기록을 확인해 같은 알림이 중복 생성되지 않게 합니다.

## 운영 가시성과 정량 검증

AI 기능은 성공 여부만 기록하지 않고, Server가 소유한 각 단계의 시간과 결과를
Micrometer metric과 구조화 로그로 남깁니다.

```text
PLAN_RUNTIME_CALL
→ PLAN RESULT_PERSIST
→ SLOT_RESOLUTION
→ ANALYZE_RUNTIME_CALL
→ ANALYZE RESULT_PERSIST
→ PIPELINE TOTAL
```

2026-08-12 로컬 환경에서 실제 Hugging Face BERT Intent 모델과 Demo Seed를 연결해
대표 입력 `응웬반A 체류연장 준비해줘`를 10회 반복 측정했습니다.

| 측정 항목 | 결과 |
| --- | --- |
| PLAN → Slot 보충 → ANALYZE 성공률 | 10/10 · 100% |
| Server 내부 Pipeline | p50 30.8ms · p95 58.7ms |
| 비동기 `202 Accepted` 응답 | 중앙값 40.0ms · p95 51.4ms |
| Client polling 포함 최종 상태 확인 | 중앙값 77.5ms · p95 114.2ms |
| Prometheus 단계 측정 | 10회 × 6단계 = 60건 |
| 의도적 대상 오류 | `TARGET_NOT_FOUND` · `SLOT_RESOLUTION`로 식별 |

PLAN·ANALYZE Runtime 호출이 전체 평균 시간의 약 74%를 차지해, 병목이 Server DB보다
모델 호출 구간에 있다는 점도 확인했습니다. Metric에는 발화문·실명·전화번호를
label로 넣지 않으며, `phase`, `stage`, `outcome`, 제한된 `failure_code`만 사용합니다.

이 값은 로컬 단일 사용자·모델 사전 로딩 이후의 개발 기준선이며 운영 SLA나 HR
업무시간 절감률이 아닙니다. 배포 환경에서는 GPU, A.X routing, cold start, OCR과
HWP 생성까지 포함해 다시 측정합니다. 실행 방법과 PromQL은
[AI 파이프라인 관측·Prometheus 가이드](docs/ai-pipeline-observability.md)를 확인합니다.

## 아키텍처

Server는 하나의 Spring Boot 애플리케이션과 PostgreSQL로 배포하는
**modular monolith**입니다. 기능별 패키지 안에서 `api → application → domain`
방향을 지키고, JPA·HTTP·Storage 구현은 `infrastructure`에 둡니다.

```text
src/main/java/com/fowoco/server/
├── common
├── auth / company
├── worker / workerimport / document / file
├── workflow / task / casework
├── approval / audit
├── workerlink / stayverification / dashboard / notification / settings
├── airun / aiintegration
└── reliability
```

전체 트리, 패키지 책임과 Flyway 규칙은
[프로젝트 구조](docs/project-structure.md)를 확인합니다.

## 변하지 않는 보안 원칙

- 사업장 데이터는 인증 Context의 `company_id`로 격리합니다.
- Client가 보낸 `company_id`를 신뢰하지 않습니다.
- PLAN에는 발화문만 보내고, ANALYZE에는 Agent가 요청한 allow-list field만 넣습니다.
  데모는 합성 데이터만 사용하며 허용된 문서 업무값을 `***`로 치환하지 않습니다.
- JWT·API Key·비밀번호·Worker Link token 같은 인증정보는 AI 요청에서 항상 차단합니다.
- OCR은 HR이 선택한 서류 파일만 전용 내부 API로 전송합니다. 실행은 Outbox로 복구하고, 원본 추출값과 HR 수정값을 분리해 암호화 저장하며 일반 로그에는 값 대신 수정한 필드명만 남깁니다.
- 근로자가 제출한 파일은 HR 채택 전 공식 서류나 검증 완료 자료로 간주하지 않습니다.
- AI 결과와 요청 초안은 HR 승인 전 자동 발송하지 않습니다.
- 중요한 변경은 actor, 시각, `request_id`와 함께 감사로그에 남깁니다.
- Worker Link 원본 token, JWT, API Key와 비밀번호를 GitHub·로그·문서에 남기지 않습니다.
- 운영 Springdoc은 비활성화합니다. 공유 Swagger는 test profile에서 생성하며,
  HTTPS 데모 주소와 명시적인 CORS가 준비된 경우에만 실제 호출을 활성화합니다.

보안 문제 신고는 공개 Issue 대신 [SECURITY.md](SECURITY.md)를 따라 주세요.

## MVP 범위 밖

- 외부기관 자동 로그인·자동 제출
- AI의 법률·노무 최종 판단
- 자체 학습 모델의 필수 서비스 탑재
- 범용 OCR·대용량 일괄 파일 처리
- 실제 Blue/Green Agent 트래픽 전환

## 가장 먼저 볼 문서

| 찾는 내용 | 바로가기 | 이 문서가 기준인 이유 |
| --- | --- | --- |
| 현재 구현된 API | [Swagger](https://fowoco.github.io/server/api/) · [OpenAPI JSON](https://fowoco.github.io/server/api/openapi.json) | `main` 코드에서 자동 생성되는 실제 API 계약. HTTPS 데모 주소가 연결되면 직접 호출 가능 |
| DB 테이블·ERD | [Database 문서](https://fowoco.github.io/server/) | Flyway를 빈 PostgreSQL에 적용해 자동 생성한 구조 |
| 로컬 실행·인증·Workflow | [개발 가이드](docs/development-guide.md) | 처음 서버를 실행하고 기능 흐름을 이해하는 방법 |
| Demo Seed 수량·시나리오 | [Demo Seed 운영 시나리오](docs/demo-seed.md) | 로컬 데모 데이터의 기준 수량, 대표 흐름과 표현 한계 |
| 재계약·연장 수동 E2E | [Golden Flow 수동 시연 가이드](docs/golden-renewal-manual-e2e.md) | HR 요청부터 근로자 서류 제출, OCR 검토와 연장 업무 완료까지 직접 확인하는 순서. `./scripts/export-golden-demo-files`로 합성 여권·ARC를 생성 |
| Docker·데모 배포 | [Server 데모 배포 Runbook](docs/deployment-runbook.md) | 로컬 Compose, 필수 Secret, Smoke와 rollback 기준 |
| Figma fixture 대응표 | [Figma Demo Fixture Manifest](docs/demo-seed-fixture-manifest.md) | 화면 요구사항별 예약 데이터와 현재 API 노출 범위 |
| 패키지·모듈 경계 | [프로젝트 구조](docs/project-structure.md) | 코드를 어느 패키지에 구현해야 하는지 설명 |
| 중요한 설계 결정 | [ADR 목록](docs/adr/README.md) | 저장소 경계, API·보안, Task·AiRun, RLS 결정 원본 |
| Server ↔ AI 계약 | [AI Runtime 계약](docs/ai-runtime-contract.md) | Server가 AI에 보내고 받을 수 있는 값과 검증 기준 |
| 근로자 명단 가져오기 | [Worker Import 가이드](docs/worker-import.md) | CSV/XLSX 업로드부터 검증·수정·등록까지의 API 순서 |
| 퇴사 근로자 보관 | [근로자 안전 보관 가이드](docs/worker-archive.md) | 삭제 없이 운영 대상에서 분리하는 조건·API·감사 기준 |
| Agent DB 정보 보충 | [Slot 조회·재호출](docs/ai-slot-resolution.md) | canonical key allow-list, tenant 조회와 ANALYZE 재호출 기준 |
| AI 단계별 성능 측정 | [AI 파이프라인 관측·Prometheus 가이드](docs/ai-pipeline-observability.md) | PLAN·Slot·ANALYZE·Renewal 구간의 정량 평가와 로컬 Prometheus 확인 기준 |
| 이벤트 유실·재처리 | [Outbox 운영 가이드](docs/reliability/transactional-outbox.md) | 이벤트 발행, lease, 재시도와 장애 복구 기준 |
| 체류기간 경과 안전 확인 | [체류기간 만료 경과 긴급 확인](docs/stay-verification.md) | 날짜 경과와 적법 체류·고용 종료 판단을 분리하는 기준 |
| 파일 rollback·orphan 대응 | [File Storage rollback 보상 운영 가이드](docs/reliability/file-storage-rollback-compensation.md) | atomic finalize, rollback cleanup, `UNKNOWN` reconciliation과 배포 volume Smoke 기준 |
| 구현 계획·업무 상태 | [Server Roadmap](https://github.com/orgs/fowoco/projects/3) · [Issues](https://github.com/fowoco/server/issues) | 실제 담당자, 우선순위와 진행 상태 |
| 전체 설명·운영 가이드 | [Server Wiki](https://github.com/fowoco/server/wiki) | 초보자용 아키텍처·API·배포 설명 |

추가 링크:
[API 문서 사용법](docs/api-documentation.md) ·
[DB 문서 사용법](docs/database-documentation.md) ·
[RLS 적용 가이드](docs/database/postgresql-rls-rollout.md) ·
[Notion API 명세](https://app.notion.com/p/f250e15aa74e82b8872581be4d7c6c3c?v=f280e15aa74e82ce8d6e8848514d41c3&pvs=23) ·
[Figma](https://www.figma.com/design/eaOD8OXZOGq6vK4H9pGXNi/FOWOCO?node-id=143-2&t=YbytLHiwZ5m1IChO-1) ·
[Discussions](https://github.com/fowoco/server/discussions)
