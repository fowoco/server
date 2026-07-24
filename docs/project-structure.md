# FOWOCO Server 프로젝트 구조

Server는 하나의 Spring Boot 애플리케이션과 PostgreSQL로 배포하는
**modular monolith**입니다. 배포 단위를 기능마다 나누기 전에 코드의 책임과
의존 방향을 패키지로 분리합니다.

## 전체 구조

```text
server/
├── build.gradle
├── settings.gradle
├── .env.example
├── README.md
├── CONTRIBUTING.md
├── docs/
│   ├── development-guide.md
│   ├── project-structure.md
│   ├── api-documentation.md
│   ├── database-documentation.md
│   └── adr/
└── src/
    ├── main/
    │   ├── java/com/fowoco/server/
    │   │   ├── ServerApplication.java
    │   │   ├── common/
    │   │   ├── health/
    │   │   ├── auth/
    │   │   ├── company/
    │   │   ├── worker/
    │   │   ├── document/
    │   │   ├── file/
    │   │   ├── workflow/
    │   │   ├── task/
    │   │   ├── approval/
    │   │   ├── audit/
    │   │   ├── workerlink/
    │   │   ├── airun/
    │   │   ├── aiintegration/
    │   │   └── reliability/
    │   └── resources/
    │       ├── application.yaml
    │       ├── workflow/
    │       └── db/
    │           ├── migration/
    │           └── migration-postgresql/
    └── test/
        └── java/com/fowoco/server/
```

빈 패키지를 미리 만드는 대신 실제 책임이 생길 때 필요한 하위 패키지를
추가합니다.

## 기능별 책임

| 패키지 | 책임 |
| --- | --- |
| `common` | 설정, 공통 오류, ID·Clock, Security와 Web 공통 코드 |
| `auth` | 회원가입, 로그인, JWT, Refresh Token |
| `company` | 사업장과 사용자 권한 |
| `worker` | 근로자 기본정보와 업무용 Context |
| `document` | 서류 메타데이터 |
| `file` | Local·S3 호환 파일 저장 Port |
| `workflow` | 배포된 Knowledge Workflow projection 조회 |
| `task` | 업무카드, Checklist와 상태 전이 |
| `approval` | 승인 요청, 승인·반려와 snapshot |
| `audit` | append-only 감사 이벤트 |
| `workerlink` | 로그인 없는 근로자 보안 링크 |
| `airun` | AI 실행, Candidate, Attempt와 retry 상태 |
| `aiintegration` | AI Runtime HTTP 계약과 Client |
| `reliability` | Outbox, event 전달과 복구 |

## 기능 내부 구조

기능 코드가 커지면 아래 방향으로 확장합니다.

```text
<feature>/
├── api/             # Controller와 HTTP request·response DTO
├── application/     # Use case, command·query, Port, Transaction 조율
├── domain/          # Aggregate, value object, 상태 전이와 불변식
└── infrastructure/  # JPA, HTTP Client, Storage 등 Port 구현
```

의존 방향은 다음을 기준으로 합니다.

```text
api → application → domain
          ↑
infrastructure
```

- `api`는 `application` Use case만 호출합니다.
- Controller가 JPA Repository나 외부 Client를 직접 호출하지 않습니다.
- `domain`은 Spring MVC, JPA, Provider SDK에 의존하지 않습니다.
- 다른 기능의 `infrastructure`와 JPA Entity를 직접 import하지 않습니다.
- 다른 기능이 Task 상태를 임의로 수정하지 않고 Task Use case를 호출합니다.

## 저장소 경계

```text
Client / Worker Link
        ↓
Spring Boot Server
   ↙          ↘
PostgreSQL   AI Runtime
                 ↓
       Knowledge Bundle + LLM
```

- `server`는 인증·권한·업무 상태·승인·감사와 영속 실행 기록을 소유합니다.
- `ai`는 Prompt, Agent Pipeline, Provider와 모델 호출을 소유합니다.
- `knowledge`는 Intent·Slot·Workflow Catalog와 공식 근거 release를 소유합니다.
- `client`는 화면 상태와 사용자 상호작용을 소유합니다.
- `infra`는 통합 배포, 네트워크, Secret과 관측 인프라를 소유합니다.

따라서 `server`의 `aiintegration`에는 Provider SDK나 Prompt Builder를 넣지
않습니다. `workflow`은 Knowledge projection을 읽지만 원본 정의를 수정하지
않습니다.

상세 결정은
[ADR-0001](adr/0001-repository-and-module-boundaries.md)에서 확인합니다.

## Flyway 규칙

- 공통 Migration은 `src/main/resources/db/migration`에 둡니다.
- PostgreSQL에서만 사용하는 Role·RLS는 `db/migration-postgresql`에 둡니다.
- `main`에 적용된 Migration 파일을 수정·삭제하지 않습니다.
- 오류는 다음 번호의 forward Migration으로 고칩니다.
- 의존 Schema가 `main`에 병합된 후 다음 사용 가능한 번호를 사용합니다.
- 번호 예약을 위한 빈 Migration은 만들지 않습니다.
- JPA `ddl-auto`로 운영 Schema를 자동 변경하지 않습니다.

현재 구조는 [Database 문서](https://fowoco.github.io/server/)에서 확인합니다.

## 테스트 구조

테스트 패키지는 기능 패키지를 따라갑니다.

```text
src/test/java/com/fowoco/server/
├── architecture/
├── auth/
├── worker/
├── task/
├── approval/
├── aiintegration/
├── reliability/
└── common/security/
```

- Domain 불변식은 빠른 단위 테스트로 검증합니다.
- Controller·Security·Transaction은 통합 테스트로 검증합니다.
- PostgreSQL 전용 제약·동시성·RLS는 CI PostgreSQL 17 환경에서 검증합니다.
- API 변경은 OpenAPI Schema와 JSON 직렬화 계약도 확인합니다.
- Migration 변경은 Flyway `migrate`, `validate`와 실제 제약 동작을 확인합니다.

## 새 기능을 어디에 만들까요?

| 만들려는 것 | 위치 |
| --- | --- |
| HTTP Endpoint·DTO | 해당 기능의 `api` |
| 업무 Use case·Transaction | 해당 기능의 `application` |
| 상태 전이·업무 규칙 | 해당 기능의 `domain` |
| JPA Repository·Entity | 해당 기능의 `infrastructure/persistence` |
| 외부 HTTP 연결 | 해당 기능의 `infrastructure` 또는 전용 integration 기능 |
| 공통 Error·Security·Web 설정 | 여러 기능에서 실제로 공유될 때만 `common` |
| Prompt·Provider SDK | 이 저장소가 아니라 `fowoco/ai` |
| Workflow 원본·공식자료 | 이 저장소가 아니라 `fowoco/knowledge` |

경계가 애매하다면 구현 전에
[Discussions](https://github.com/fowoco/server/discussions)에서 합의하거나 새로운
ADR을 `Proposed`로 작성합니다.
