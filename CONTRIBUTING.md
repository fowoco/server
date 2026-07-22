# FOWOCO Server 기여 가이드

처음 참여한 개발자가 어디서 시작하고 무엇을 확인해야 하는지 설명합니다.

## 1. 질문인가요, 작업인가요?

- 답이 아직 없거나 여러 선택지를 비교해야 하면 [Discussion](https://github.com/fowoco/server/discussions)에 작성합니다.
- 구현 범위·담당자·완료 조건이 정해졌으면 [Issue](https://github.com/fowoco/server/issues/new/choose)를 만듭니다.
- Discussion에서 결론이 나면 실행할 내용을 Issue로 옮깁니다. 오래 유지할 구조·API·상태 결정은 [ADR](docs/adr/README.md)을 `Proposed`로 작성해 PR에서 합의하고, `Accepted` 후 Wiki·Notion 설명을 동기화합니다.

실제 근로자 정보, JWT, Worker Link 원본 토큰, API Key, 비밀번호, 전체 `.env`, 전체 로그는 올리지 않습니다.

## 2. 작업 선택

1. [MVP Epic #2](https://github.com/fowoco/server/issues/2)에서 소유 Issue를 확인합니다.
2. 팀원 전용 [Server Roadmap](https://github.com/orgs/fowoco/projects/3)에서 `진행 상태 = 준비됨`을 찾습니다.
3. Issue의 선행 작업, 범위 밖, 완료 조건을 읽습니다.
4. 담당자를 지정하고 Project의 상세 진행 상태를 `진행 중`으로 바꿉니다.
5. 브랜치명에 Issue 번호를 포함합니다.

```text
feat/4-auth-multitenancy
fix/7-worker-link-expiry
docs/23-architecture-adr
```

`main`만 장기 브랜치로 사용하고 `develop`, 개인 장기 브랜치는 만들지 않습니다. 모든 작업 브랜치는 최신 `main`에서 만들며, 브랜치명은 도구 호환성을 위해 영문·숫자·하이픈을 사용합니다. 한 브랜치는 한 사람이 소유하고 `main`에 직접 push하지 않습니다.

### Commit과 PR 작성 규칙

- Commit message는 Conventional Commits 형식을 사용합니다.
- type과 scope는 영문으로 쓰고, commit subject도 간결한 영문으로 작성합니다.
- PR 제목은 한국어로 핵심을 설명합니다. 코드 식별자와 일반적인 기술 용어는 영어를 그대로 사용해도 됩니다.
- PR 본문은 변경 이유와 영향, 검증 결과가 명확하게 전달되는 것을 우선합니다. 코드 식별자와 기술 용어는 억지로 번역하지 않습니다.

```text
feat(auth): implement access token refresh
fix(worker-link): validate token expiration
chore: configure server development environment
docs: update local setup guide

PR title: feat: 인증 API와 Refresh Token rotation 구현
```

## 3. 로컬 검증

```bash
./gradlew clean test
./gradlew bootRun
curl http://localhost:8080/health
```

브라우저에서 <http://localhost:8080/swagger-ui.html>도 열리는지 확인합니다. 기본 `local` Profile은 H2를 사용하므로 PostgreSQL 설치 없이 시작할 수 있습니다.

## 4. 구현 기본 순서

1. Accepted ADR과 관련 Issue에서 저장소·모듈·상태 경계를 확인합니다.
2. Notion·Wiki·Figma에서 사용 목적과 화면 흐름을 확인합니다.
3. 구현할 API의 OpenAPI, Request/Response DTO와 오류 케이스를 정의합니다.
4. Controller는 HTTP 변환만 담당합니다.
5. Application Service에서 역할, `company_id`, 상태 전이, 승인·증빙 조건을 검사합니다.
6. Repository와 Flyway migration을 구현합니다.
7. 정상·검증 실패·권한 부족·타 사업장 접근 테스트를 작성합니다.
8. Swagger와 필요한 Notion·Wiki 설명을 갱신합니다.

### Flyway migration 규칙

- 최신 `main`의 다음 번호를 Issue와 PR에서 예약합니다.
- 같은 번호를 두 브랜치에서 사용하지 않습니다.
- `main`에 병합되어 공유 환경에 적용된 migration은 수정·삭제·번호 변경하지 않습니다.
- PR 검토 중인 migration을 바꿀 때는 예약 번호와 공유 환경 적용 여부를 먼저 확인합니다.
- migration은 가능한 한 PR 하나에 하나만 포함합니다.
- PostgreSQL CI가 validation과 pending migration 없음까지 확인해야 합니다.

## 5. Architecture Decision

아래 변경은 코드보다 먼저 ADR을 작성하거나 기존 Accepted ADR을 확인합니다.

- 저장소·모듈 책임 변경
- 공개 또는 내부 API namespace 변경
- Task·AiRun·승인 상태와 전이 변경
- 인증·tenant·PII 정책 변경
- 외부 서비스, Event, retry와 transaction 경계 변경

ADR은 [작성 절차](docs/adr/README.md)를 따릅니다. Accepted ADR을 의미가 달라지도록 직접 고치지 않고, 새 ADR에서 기존 결정을 `Superseded`합니다.

## 6. PR

PR Template을 채우고 관련 Issue를 연결합니다.

- 전체 작업 완료: `Closes #번호`
- 일부 참고·진행: `Refs #번호`

PR 전 확인:

- `./gradlew clean test`가 통과하는가?
- 다른 사업장의 데이터를 조회·수정할 수 없는가?
- 민감정보·토큰·Secret이 DTO·로그·AI 입력에 없는가?
- AI 오류가 자동 승인·발송으로 이어지지 않는가?
- Accepted ADR과 저장소 경계를 지켰는가?
- Server에 Prompt Builder·Provider SDK·모델 routing을 추가하지 않았는가?
- API 변경이 Swagger·Notion·Client와 동기화됐는가?
- DB 변경에 migration과 롤백 고려가 있는가?

## 7. 완료

코드 병합, CI, migration, Swagger, Wiki, 필요한 배포·Smoke Test까지 Issue 완료 조건을 모두 충족한 뒤 Project 상태를 완료로 바꿉니다.
