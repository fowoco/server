# FOWOCO Server 기여 가이드

처음 참여한 개발자가 어디서 시작하고 무엇을 확인해야 하는지 설명합니다.

## 1. 질문인가요, 작업인가요?

- 답이 아직 없거나 여러 선택지를 비교해야 하면 [Discussion](https://github.com/fowoco/server/discussions)에 작성합니다.
- 구현 범위·담당자·완료 조건이 정해졌으면 [Issue](https://github.com/fowoco/server/issues/new/choose)를 만듭니다.
- Discussion에서 결론이 나면 실행할 내용을 Issue로 옮기고, 오래 유지할 결정은 Wiki에도 반영합니다.

실제 근로자 정보, JWT, Worker Link 원본 토큰, API Key, 비밀번호, 전체 `.env`, 전체 로그는 올리지 않습니다.

## 2. 작업 선택

1. [MVP Epic #2](https://github.com/fowoco/server/issues/2)에서 소유 Issue를 확인합니다.
2. 팀원 전용 [Project](https://github.com/orgs/fowoco/projects/1)에서 `진행 상태 = 준비됨`을 찾습니다.
3. Issue의 선행 작업, 범위 밖, 완료 조건을 읽습니다.
4. 담당자를 지정하고 Project의 상세 진행 상태를 `진행 중`으로 바꿉니다.
5. 브랜치명에 Issue 번호를 포함합니다.

```text
feat/4-auth-multitenancy
fix/7-worker-link-expiry
docs/13-file-storage-guide
agent/3-dev-environment-foundation
```

브랜치명은 도구 호환성을 위해 영문·숫자·하이픈을 사용합니다. Codex가 작업할 때는 `agent/{Issue 번호}-{짧은 설명}` 형식을 사용합니다.

### Commit과 PR 작성 규칙

- Commit message는 Conventional Commits 형식을 사용합니다.
- type과 scope는 영문으로 쓰고, commit subject도 간결한 영문으로 작성합니다.
- PR 제목은 `type: 한글 요약` 형식으로 작성합니다.
- PR 본문은 변경 이유와 영향, 검증 결과가 명확하게 전달되는 것을 우선합니다. 코드 식별자와 기술 용어는 억지로 번역하지 않습니다.

```text
feat(auth): implement access token refresh
fix(worker-link): validate token expiration
chore: configure server development environment
docs: update local setup guide

PR title: feat: 인증 API 구현
```

## 3. 로컬 검증

```bash
./gradlew clean test
./gradlew bootRun
curl http://localhost:8080/health
```

브라우저에서 <http://localhost:8080/swagger-ui.html>도 열리는지 확인합니다. 기본 `local` Profile은 H2를 사용하므로 PostgreSQL 설치 없이 시작할 수 있습니다.

## 4. 구현 기본 순서

1. Notion과 Wiki에서 API 목적·역할·상태 규칙을 확인합니다.
2. Request/Response DTO와 오류 케이스를 정의합니다.
3. Controller는 HTTP 변환만 담당합니다.
4. Service에서 역할, `company_id`, 상태 전이, 승인·증빙 조건을 검사합니다.
5. Repository와 Flyway migration을 구현합니다.
6. 정상·검증 실패·권한 부족·타 사업장 접근 테스트를 작성합니다.
7. Swagger 예시와 관련 문서를 갱신합니다.

## 5. PR

PR Template을 채우고 관련 Issue를 연결합니다.

- 전체 작업 완료: `Closes #번호`
- 일부 참고·진행: `Refs #번호`

PR 전 확인:

- `./gradlew clean test`가 통과하는가?
- 다른 사업장의 데이터를 조회·수정할 수 없는가?
- 민감정보·토큰·Secret이 DTO·로그·AI 입력에 없는가?
- AI 오류가 자동 승인·발송으로 이어지지 않는가?
- API 변경이 Swagger·Notion·Client와 동기화됐는가?
- DB 변경에 migration과 롤백 고려가 있는가?

## 6. 완료

코드 병합, CI, migration, Swagger, Wiki, 필요한 배포·Smoke Test까지 Issue 완료 조건을 모두 충족한 뒤 Project 상태를 완료로 바꿉니다.
