# Architecture Decision Records

이 디렉터리는 FOWOCO Server의 장기적인 아키텍처 결정을 기록하는 원본입니다. ADR(Architecture Decision Record)은 코드가 무엇을 하는지만이 아니라 **왜 그 구조를 선택했고 어떤 선택을 포기했는지**를 남깁니다.

## 문서 우선순위

문서가 서로 다를 때는 다음 순서로 확인합니다.

1. Server 구조·상태·보안 결정: 이 디렉터리의 `Accepted` ADR
2. 현재 구현된 Server External API: 배포된 Swagger/OpenAPI와 자동화 테스트
3. Server가 소비하는 AI Internal API·Structured Output: `fowoco/ai`의 versioned OpenAPI·JSON Schema
4. Intent·Workflow Catalog·Context Pack: `fowoco/knowledge`의 immutable bundle
5. 사람이 읽는 사용 예시와 화면 설명: Notion·Wiki·Figma

Wiki와 Notion은 중요한 내용을 이해하기 쉽게 설명하는 mirror입니다. 실행 계약의 원본을 대체하지 않으며, 차이를 발견하면 같은 PR에서 고치거나 Issue로 기록합니다.

## 상태

| 상태 | 의미 |
| --- | --- |
| `Proposed` | PR에서 검토 중이며 아직 팀 합의가 끝나지 않음 |
| `Accepted` | 팀 리뷰와 CI를 통과해 현재 따라야 하는 결정 |
| `Rejected` | 검토했지만 선택하지 않은 결정 |
| `Superseded` | 새로운 ADR이 대신하고 있는 이전 결정 |

`Accepted` ADR의 결론을 조용히 다시 쓰지 않습니다. 결정을 변경할 때는 새 ADR을 만들고 새 ADR의 `Supersedes`에 이전 번호를 적은 뒤, 이전 ADR을 `Superseded`로 변경합니다. 오탈자와 링크처럼 의미를 바꾸지 않는 수정은 기존 ADR에서 바로 고칠 수 있습니다.

## 목록

| ADR | 제목 | 상태 |
| --- | --- | --- |
| [ADR-0001](0001-repository-and-module-boundaries.md) | 저장소·모듈 경계와 계약 소유권 | Accepted |
| [ADR-0002](0002-api-security-and-error-contract.md) | API·보안·오류 계약 | Accepted |
| [ADR-0003](0003-task-airun-event-and-retry-model.md) | Task·AiRun·Event·Retry 모델 | Accepted |
| [ADR-0004](0004-postgresql-rls-tenant-isolation.md) | PostgreSQL RLS 기반 tenant 2차 격리와 안전한 도입 | Accepted |

## 작성과 승인 절차

1. Discussion 또는 Issue에서 결정해야 할 문제와 범위를 확인합니다.
2. 최신 `main`에서 `docs/{issue-number}-{description}` 브랜치를 만듭니다.
3. ADR을 `Proposed`로 작성하고 대안과 영향을 함께 기록합니다.
4. Draft PR에서 영향받는 저장소와 모듈 담당자가 검토합니다.
5. 합의한 ADR을 `Accepted`로 바꾸고 마지막 승인을 받습니다.
6. CI 통과 후 Squash Merge합니다.
7. 관련 Wiki·Notion과 후속 Issue가 같은 용어를 쓰는지 확인합니다.

Architecture PR은 최소한 다음 질문에 답해야 합니다.

- 이 책임과 계약의 원본은 어느 저장소인가?
- 어느 모듈이 상태를 최종 변경할 수 있는가?
- 개인정보·권한·tenant 경계는 어디에서 강제하는가?
- 실패·재시도·중복 요청을 누가 처리하는가?
- 대안과 비용은 무엇이며 어떻게 되돌릴 수 있는가?
