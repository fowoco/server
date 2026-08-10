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
흐름으로 연결합니다.

| 구분 | 현재 상태 |
| --- | --- |
| 핵심 업무 API | Auth·Worker·Document·Task·Approval·Worker Link·Case 구현 |
| AI 연동 | PLAN → Slot 보충 → ANALYZE, Candidate 결정, AiRun·SSE 이력 구현 |
| 문서 처리 | 파일 저장·다운로드, HWP/HWPX 검증, OCR 실행·HR 검토 구현 |
| 운영 기반 | Flyway, PostgreSQL 16, RLS 검증, Transactional Outbox, 감사로그 구현 |
| 마무리 중 | Renewal 실행·생성 문서 연결, 알림 이벤트 생성, HTTPS 배포·제품 E2E |

## 왜 이 기술과 구조를 선택했는가

> 기술의 인지도보다 **세 명이 만드는 배포 가능한 MVP**, **사업장 데이터 격리**,
> **AI 결과의 통제**, **장애 후 업무 복구**에 적합한지를 선택 기준으로 삼았습니다.

| 선택 | 해결해야 했던 서비스 문제 | 판단과 적용 |
| --- | --- | --- |
| Spring Boot modular monolith | 인증·Task·승인·감사로그가 하나의 업무 트랜잭션으로 연결됨 | Microservices의 분산 트랜잭션·배포 부담을 먼저 만들지 않고 데이터 일관성과 개발 속도를 확보했습니다. 대신 패키지와 Port로 경계를 나눠 AI 실행·파일 처리처럼 독립성이 커지는 영역부터 추후 분리할 수 있게 했습니다. |
| PostgreSQL·Flyway·RLS | 사업장별 데이터와 상태 전이·승인 이력을 관계와 트랜잭션으로 보존해야 함 | PostgreSQL을 업무 상태의 단일 기준으로 두고, Flyway로 재현 가능한 변경 이력을 관리했습니다. 애플리케이션의 `company_id` 조건에만 의존하지 않고 복합 FK와 RLS로 DB 격리를 보강했습니다. |
| Spring Security·JWT·RBAC | HR 사용자 역할과 사업장 권한을 모든 API에서 일관되게 검사해야 함 | Access Token은 stateless JWT로 검증하고 `ActorContext`에서 사용자·역할·사업장을 결정했습니다. 계정이 없는 근로자는 별도의 만료형 Worker Link로 제한된 기능만 사용하게 했습니다. |
| Server–AI HTTP 경계와 allow-list Resolver | AI가 업무 DB를 직접 조회하면 권한 우회와 개인정보 과다 전달 위험이 있음 | AI Runtime의 DB 직접 접근을 허용하지 않고, PLAN에서 요청한 허용 field만 Server가 tenant 범위로 조회해 ANALYZE에 보충하도록 설계했습니다. 판단·권한·영속 상태는 Server가 소유합니다. |
| REST + AiRun + SSE | AI 실행은 오래 걸리지만 Client가 보내야 하는 실시간 메시지는 없음 | 요청과 결과는 재조회 가능한 AiRun resource로 저장하고, 단방향 상태 알림은 WebSocket보다 단순한 SSE를 사용했습니다. 연결이 끊겨도 DB 상태를 다시 조회할 수 있게 했습니다. |
| PostgreSQL Transactional Outbox | DB 변경 성공 후 알림·OCR 같은 후속 실행이 실패하면 업무가 유실될 수 있음 | 별도 Broker를 먼저 운영하는 대신 업무 변경과 Event를 같은 DB 트랜잭션에 저장했습니다. lease·backoff·멱등성·수동 재처리로 장애 후에도 처리 상태가 수렴하도록 했습니다. |

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

## 가장 먼저 볼 문서

| 찾는 내용 | 바로가기 | 이 문서가 기준인 이유 |
| --- | --- | --- |
| 현재 구현된 API | [Swagger](https://fowoco.github.io/server/api/) · [OpenAPI JSON](https://fowoco.github.io/server/api/openapi.json) | `main` 코드에서 자동 생성되는 실제 API 계약. HTTPS 데모 주소가 연결되면 직접 호출 가능 |
| DB 테이블·ERD | [Database 문서](https://fowoco.github.io/server/) | Flyway를 빈 PostgreSQL에 적용해 자동 생성한 구조 |
| 로컬 실행·인증·Workflow | [개발 가이드](docs/development-guide.md) | 처음 서버를 실행하고 기능 흐름을 이해하는 방법 |
| Demo Seed 수량·시나리오 | [Demo Seed 운영 시나리오](docs/demo-seed.md) | 로컬 데모 데이터의 기준 수량, 대표 흐름과 표현 한계 |
| Docker·데모 배포 | [Server 데모 배포 Runbook](docs/deployment-runbook.md) | 로컬 Compose, 필수 Secret, Smoke와 rollback 기준 |
| Figma fixture 대응표 | [Figma Demo Fixture Manifest](docs/demo-seed-fixture-manifest.md) | 화면 요구사항별 예약 데이터와 현재 API 노출 범위 |
| 패키지·모듈 경계 | [프로젝트 구조](docs/project-structure.md) | 코드를 어느 패키지에 구현해야 하는지 설명 |
| 중요한 설계 결정 | [ADR 목록](docs/adr/README.md) | 저장소 경계, API·보안, Task·AiRun, RLS 결정 원본 |
| Server ↔ AI 계약 | [AI Runtime 계약](docs/ai-runtime-contract.md) | Server가 AI에 보내고 받을 수 있는 값과 검증 기준 |
| 근로자 명단 가져오기 | [Worker Import 가이드](docs/worker-import.md) | CSV/XLSX 업로드부터 검증·수정·등록까지의 API 순서 |
| Agent DB 정보 보충 | [Slot 조회·재호출](docs/ai-slot-resolution.md) | canonical key allow-list, tenant 조회와 ANALYZE 재호출 기준 |
| 이벤트 유실·재처리 | [Outbox 운영 가이드](docs/reliability/transactional-outbox.md) | 이벤트 발행, lease, 재시도와 장애 복구 기준 |
| 구현 계획·업무 상태 | [Server Roadmap](https://github.com/orgs/fowoco/projects/3) · [Issues](https://github.com/fowoco/server/issues) | 실제 담당자, 우선순위와 진행 상태 |
| 전체 설명·운영 가이드 | [Server Wiki](https://github.com/fowoco/server/wiki) | 초보자용 아키텍처·API·배포 설명 |

추가 링크:
[API 문서 사용법](docs/api-documentation.md) ·
[DB 문서 사용법](docs/database-documentation.md) ·
[RLS 적용 가이드](docs/database/postgresql-rls-rollout.md) ·
[Notion API 명세](https://app.notion.com/p/f250e15aa74e82b8872581be4d7c6c3c?v=f280e15aa74e82ce8d6e8848514d41c3&pvs=23) ·
[Figma](https://www.figma.com/design/eaOD8OXZOGq6vK4H9pGXNi/FOWOCO?node-id=143-2&t=YbytLHiwZ5m1IChO-1) ·
[Discussions](https://github.com/fowoco/server/discussions)

## Server가 담당하는 일

- 사업장 사용자 인증과 `ADMIN`·`HR`·`VIEWER` 권한
- `company_id`를 기준으로 한 사업장 데이터 격리
- 근로자 기본정보와 서류 메타데이터 관리
- CSV/XLSX 근로자 명단 가져오기와 OCR 검토
- 업무카드·체크리스트·상태 전이 관리
- HR 승인·반려·외부 제출·증빙·완료와 감사로그
- 만료되는 근로자 보안 링크
- AI Runtime PLAN·ANALYZE 요청, Slot 보충, 응답 검증과 영속 실행 이력
- Today Dashboard·알림·사업장 설정 조회
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

## 현재 구현 기준

고정된 API 개수를 README에 적지 않습니다. API는 계속 변경되므로 현재 구현
여부는 [Swagger](https://fowoco.github.io/server/api/)와 자동화 테스트를
기준으로 확인합니다.

| 영역 | `main`에서 확인할 수 있는 내용 |
| --- | --- |
| Auth·Company | 회원가입, 로그인, JWT·Refresh Token, 비밀번호 재설정·SMTP, 사업장 설정 |
| Worker·Document·File | 근로자·서류 CRUD, 문서 준비도·요청 초안, 파일 업로드·권한 기반 다운로드 |
| Worker Import·OCR | CSV/XLSX mapping·검증·선택 등록, 신분서류 OCR 실행·암호화 결과·HR 검토 |
| Task·Workflow·Case | Catalog projection, 업무카드·체크리스트·상태 전이, 복합 Case 조회 |
| Approval·Audit | 승인·반려·외부 제출·증빙·완료와 변경 이력·감사 이벤트 |
| Worker Link | 만료·회전 가능한 보안 링크, 전달 상태, 근로자 응답·파일 제출·HR 확인 |
| AI Integration·AiRun | PLAN/ANALYZE, Slot Resolver, Candidate 결정, retry, SSE·실행 이력 |
| Dashboard·Notification | Today 요약, 만료·추천 정보, 사용자별 알림 조회·읽음 처리 |
| Reliability·Database | PostgreSQL 16, Flyway, RLS 검증, Transactional Outbox·수동 재처리 |
| Documentation | Swagger/OpenAPI와 Database 문서의 GitHub Pages 자동 배포 |

계획 중인 API를 현재 구현된 것처럼 표시하지 않습니다. 아직 병합되지 않은 범위는
[Issues](https://github.com/fowoco/server/issues)와
[Roadmap](https://github.com/orgs/fowoco/projects/3)에서 확인합니다.

## 5분 실행

### 준비물

- JDK 17
- Git
- PostgreSQL은 `dev` Profile을 사용할 때만 필요

```bash
git clone https://github.com/fowoco/server.git
cd server
./gradlew clean test
./gradlew bootRun
```

기본 Profile은 H2를 사용하는 `local`이므로 별도 DB가 필요하지 않습니다.

```bash
curl http://localhost:8080/health
```

정상이면 `OK`가 반환됩니다.

| 로컬 도구 | 주소 |
| --- | --- |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| H2 Console | <http://localhost:8080/h2-console> |

PostgreSQL 실행과 회원가입·로그인은 [개발 가이드](docs/development-guide.md),
Demo Seed의 수량과 대표 업무 구성은
[Demo Seed 운영 시나리오](docs/demo-seed.md)에서 확인합니다.

## 대표 흐름

```text
HR 로그인
→ 근로자·서류 확인
→ 자연어 또는 D-day 이벤트 분석
→ 업무카드 후보 검토·확정
→ 필요정보와 문서 초안 확인
→ HR 승인
→ 외부 제출·처리결과 기록
→ 완료·감사로그
```

대표 입력:

> 응웬반A가 3년 만료 예정이야. 재계약하고 체류연장 준비해줘.

목표 결과는 재계약·취업활동기간 연장·체류기간 연장 Workflow를 분리해 만들고,
누락정보를 담당자에게 질문하는 것입니다. FOWOCO가 기관에 자동 로그인하거나
신청서를 대신 제출하지는 않습니다.

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
├── workerlink / dashboard / notification / settings
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
- AI 결과와 요청 초안은 HR 승인 전 자동 발송하지 않습니다.
- 중요한 변경은 actor, 시각, `request_id`와 함께 감사로그에 남깁니다.
- Worker Link 원본 token, JWT, API Key와 비밀번호를 GitHub·로그·문서에 남기지 않습니다.
- 운영 Springdoc은 비활성화합니다. 공유 Swagger는 test profile에서 생성하며,
  HTTPS 데모 주소와 명시적인 CORS가 준비된 경우에만 실제 호출을 활성화합니다.

보안 문제 신고는 공개 Issue 대신 [SECURITY.md](SECURITY.md)를 따라 주세요.

## 기여하기

처음 참여한다면 [개발 협업 가이드](docs/team-development-guide.md)를 먼저 읽습니다.

1. [Roadmap](https://github.com/orgs/fowoco/projects/3)과 Issue의 담당·선행조건을 확인합니다.
2. `main`에서 짧은 기능 브랜치를 만듭니다.
3. 코드와 함께 테스트·OpenAPI·Migration·문서 영향을 확인합니다.
4. PR에 관련 Issue, 변경 이유, 검증 결과와 보안 영향을 작성합니다.
5. 리뷰와 CI 통과 후 Squash Merge합니다.

질문·아이디어·합의 전 설계는
[Discussions](https://github.com/fowoco/server/discussions)에 작성합니다.

## MVP 범위 밖

- 외부기관 자동 로그인·자동 제출
- AI의 법률·노무 최종 판단
- 자체 학습 모델의 필수 서비스 탑재
- 범용 OCR·대용량 일괄 파일 처리
- 실제 Blue/Green Agent 트래픽 전환
