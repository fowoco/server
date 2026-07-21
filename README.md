# FOWOCO Server

E-9 외국인근로자를 고용한 사업장의 HR·총무 업무를 안전한 Workflow로 운영하는 Spring Boot 백엔드입니다.

FOWOCO는 단순 번역 서비스가 아닙니다. 체류·계약·서류·신고·근로자 안내 업무를 업무카드로 만들고, 담당자가 필요한 정보·승인·증빙·다음 행동을 놓치지 않도록 돕습니다.

> AI는 판단자가 아니라 보조자입니다. AI 결과는 인증·사업장 권한·상태 전이·HR 승인·감사 로그 안에서만 사용합니다.

## 현재 상태

2026-07-21 기준입니다.

| 항목 | 현재 |
| --- | --- |
| 기술 | Java 17, Spring Boot 4.1.0, Gradle |
| 구현 API | `GET /health` 1개 |
| 계획 API | 41개 — 전체 카탈로그 42개 |
| 로컬 DB | H2 |
| 개발·배포 DB | PostgreSQL 설정 골격 |
| 인증·AI·Workflow | Issue에서 구현 예정 |
| Swagger·Flyway·CI | 기반 Issue #3에서 구현 예정 |

계획된 API를 현재 동작한다고 오해하지 마세요. 구현 상태는 코드와 테스트를, 계획 계약은 [API 카탈로그](https://github.com/fowoco/server/wiki/09-API-Specification)를 확인합니다.

## 5분 실행

### 필요한 것

- JDK 17
- Git
- PostgreSQL은 `dev` Profile을 사용할 때만 필요

### 테스트와 실행

```bash
git clone https://github.com/fowoco/server.git
cd server
bash ./gradlew test
bash ./gradlew bootRun
```

새 터미널에서 상태를 확인합니다.

```bash
curl http://localhost:8080/health
```

정상 응답은 `OK`입니다.

`gradlew`에 실행 권한이 아직 없으므로 `./gradlew` 대신 `bash ./gradlew`를 사용합니다. 실행 권한 정리는 [#3](https://github.com/fowoco/server/issues/3)에서 추적합니다.

### PostgreSQL 개발 Profile

```bash
export DB_USERNAME=postgres
export DB_PASSWORD='로컬에서만 사용하는 값'
export SPRING_PROFILES_ACTIVE=dev
bash ./gradlew bootRun
```

실제 비밀번호·API Key·토큰은 Git, Issue, Discussion, 로그에 올리지 않습니다. `.env.example`에는 변수 이름만 기록합니다.

## 대표 사용자 흐름

```text
HR 로그인
→ 근로자·서류 등록
→ 자연어 분석
→ 업무카드 후보 검토·확정
→ HR 승인
→ 근로자 보안 링크
→ 응답·증빙
→ 완료·감사 로그
```

대표 입력:

> 응웬반A 체류연장 준비하고 여권 사본도 요청해줘

기대 결과는 체류연장과 여권 사본 요청 후보 2개입니다. HR이 선택한 후보만 실제 업무가 되고, 승인 전에는 근로자에게 전달되지 않습니다.

## 구조

```text
React Client
  → Spring Boot Backend
    → PostgreSQL
    → Local / S3-compatible File Storage
    → AI Adapter
      → External LLM API / Cloud Model Endpoint
```

- LM Studio는 개발·모델 후보 실험용입니다.
- 최종 데모는 외부 LLM API 또는 Cloud Endpoint를 사용합니다.
- Provider가 바뀌어도 내부 API와 Workflow 규칙은 유지합니다.

## 어디서 무엇을 찾나요?

| 목적 | 위치 |
| --- | --- |
| 전체 백엔드 목표·작업 순서 | [MVP Epic #2](https://github.com/fowoco/server/issues/2) |
| 42개 API 공개 요약 | [Wiki API 카탈로그](https://github.com/fowoco/server/wiki/09-API-Specification) |
| 상세 DTO·화면 기획 | [Notion API 명세](https://app.notion.com/p/f250e15aa74e82b8872581be4d7c6c3c?v=f280e15aa74e82ce8d6e8848514d41c3&pvs=23) |
| 화면·사용 흐름 | [Figma](https://www.figma.com/design/eaOD8OXZOGq6vK4H9pGXNi/FOWOCO?node-id=143-2&t=YbytLHiwZ5m1IChO-1) |
| 질문·아이디어·설계 비교 | [Discussions](https://github.com/fowoco/server/discussions) |
| 구현이 확정된 작업 | [Issues](https://github.com/fowoco/server/issues) |
| P0 핵심 일정 | [M3 Milestone](https://github.com/fowoco/server/milestone/1) |
| P1 사용성 일정 | [M4 Milestone](https://github.com/fowoco/server/milestone/2) |
| 팀 전체 진행 상태 | [Project · 팀원 전용](https://github.com/orgs/fowoco/projects/1) |
| 아키텍처·보안·배포 설명 | [Server Wiki](https://github.com/fowoco/server/wiki) |

## 변하지 않는 보안 원칙

- 모든 사업장 데이터는 인증 Context의 `company_id`로 격리합니다.
- 근로자는 로그인하지 않고 만료되는 보안 링크만 사용합니다.
- 외국인등록번호·여권번호·전화번호·계좌번호를 AI에 보내지 않습니다.
- AI 결과와 요청 초안은 HR 승인 전 자동 발송하지 않습니다.
- 중요한 변경은 actor, 시각, `request_id`와 함께 감사 로그에 남깁니다.
- Worker Link 원본 토큰, JWT, API Key, 비밀번호를 저장소와 로그에 남기지 않습니다.

## 기여하기

처음 참여한다면 [CONTRIBUTING.md](CONTRIBUTING.md)를 읽어 주세요.

- 질문이나 합의 전 아이디어는 Discussion에 작성합니다.
- 구현 범위와 완료 조건이 정해졌으면 Issue Form을 사용합니다.
- PR에는 관련 Issue, 변경 이유, 테스트, 보안 영향, 롤백 방법을 적습니다.
- 코드가 병합돼도 migration·Swagger·테스트·문서·필요한 배포가 남아 있으면 완료가 아닙니다.

## MVP 범위 밖

- 외부기관 자동 제출
- AI의 법률·노무 최종 판단
- 자체 학습 모델의 필수 서비스 탑재
- OCR·대용량 파일 처리 전체 구현
- 실제 Blue/Green Agent 트래픽 전환
