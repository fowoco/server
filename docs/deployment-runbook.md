# Server 데모 배포 Runbook

## 한 줄 설명

Server 이미지를 만드는 일과 Kubernetes에 최초 환경을 만드는 일을 분리합니다.

- `server` 저장소: 테스트, 이미지 생성, 기존 `deployment/server`의 이미지 교체, Smoke
- `infra` 저장소: Namespace, PostgreSQL, `server-env` Secret, Deployment, Service, Ingress

배포 성공은 GitHub Actions 명령이 끝난 상태가 아니라 새 Pod의 readiness와 `/health`가
모두 성공한 상태입니다.

## 현재 최초 배포 선행조건

`deploy.yml`이 매 실행마다 `fowoco/infra`의 `00-namespace.yaml`/`01-postgres.yaml`/
`02-server.yaml`을 자동으로 `kubectl apply`합니다 (idempotent self-heal, `client`/`ai`와
동일 패턴). Namespace·PostgreSQL·`deployment/server` 자체를 수동으로 먼저 만들어둘 필요는
없습니다.

다만 **Secret 두 개(`postgres-secret`, `server-env`)는 자동화 대상이 아니며, 없으면 Server
배포는 의도적으로 실패합니다**:

```bash
kubectl -n fowoco get secret/postgres-secret
kubectl -n fowoco get secret/server-env
```

Secret은 Git과 Actions 로그에 값을 남기지 않고 `kubectl create secret` 또는 승인된 Secret
관리 도구로 생성합니다. 정확한 키 목록과 생성 순서는 `fowoco/infra` Wiki의
[Deployment Guide](https://github.com/fowoco/infra/wiki/Deployment-Guide) 참고 (PostgreSQL
`fowoco_migration`/`fowoco_runtime` role 분리 절차 포함).

## `server-env` 필수 설정

| 분류 | 환경변수 | 설명 |
| --- | --- | --- |
| Profile | `SPRING_PROFILES_ACTIVE=prod` | PostgreSQL 운영 설정 사용 |
| DB | `DB_URL` | `jdbc:postgresql://postgres:5432/...` |
| DB | `DB_RUNTIME_USERNAME`, `DB_RUNTIME_PASSWORD` | 애플리케이션 실행 계정 |
| DB | `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | Flyway 전용 계정 |
| Auth | `JWT_SECRET_BASE64`, `JWT_ISSUER`, `JWT_AUDIENCE` | Access Token 발급·검증 |
| Web | `CORS_ALLOWED_ORIGINS` | 실제 Client HTTPS origin만 허용 |
| Catalog | `WORKFLOW_CATALOG_LOCATION` | 검증된 `RELEASED` projection 위치 |
| AI | `AI_RUNTIME_ENABLED=true` | 실제 Runtime 연동 활성화 |
| AI | `AI_RUNTIME_ENDPOINT` | 예: `http://ai:8000/internal/v1/analyses` |
| AI | `AI_RUNTIME_SERVICE_CREDENTIAL` | Server↔AI 내부 Bearer credential |

비밀번호 재설정 메일을 실제로 발송할 때만 다음 값을 `server-env`에 추가합니다. 기본
`PASSWORD_RESET_NOTIFICATION_PROVIDER=none`에서는 메일을 발송하지 않습니다.

| 분류 | 환경변수 | 설명 |
| --- | --- | --- |
| Mail | `PASSWORD_RESET_NOTIFICATION_PROVIDER=smtp` | SMTP Adapter 활성화 |
| Mail | `PASSWORD_RESET_CLIENT_URL` | 예: `https://demo.example.com/reset-password` |
| Mail | `PASSWORD_RESET_MAIL_FROM` | 검증된 발신자 주소 |
| Mail | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP Endpoint |
| Mail | `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP credential |
| Mail | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true` | SMTP 인증 사용 |
| Mail | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` | STARTTLS 사용 |

SMTP 비밀번호와 재설정 원본 token은 Git, Issue, 일반 로그에 기록하지 않습니다. Provider 장애가
비밀번호 재설정 요청의 외부 응답을 바꾸지 않도록 발송은 비동기로 격리되어 있습니다.

DB pool은 기본 최대 10개입니다. 클러스터 규모에 따라 `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE`,
`DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS`로 제한합니다.

현재 Infra에 HTTPS/TLS와 `RELEASED` Workflow Catalog 배포가 없으면 `prod` 완료 조건을
충족하지 못합니다. 임시 HTTP 주소와 DRAFT Catalog는 개발 Smoke에만 사용합니다.

## 로컬 PostgreSQL 통합 실행

아래 Compose는 로컬 개발용이며 운영 Secret이나 실제 개인정보를 사용하지 않습니다.

```bash
export DEMO_DB_PASSWORD='local-demo-password'
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='로컬 전용 12자 이상 값'
docker compose -f compose.demo.yml up --build
```

`DEMO_SEED_ENABLED`의 Compose 기본값은 안전하게 `false`입니다. 개인 Demo DB에서 Seed가
필요한 경우에만 위와 같이 활성화하고 12자 이상의 합성 비밀번호를 지정합니다. 첫 기동은
빈 PostgreSQL 17 DB에 Flyway와 전체 Demo Seed를 적용합니다.

확인:

```bash
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:8080/health
```

멱등성 Smoke는 서버를 중지하되 volume을 유지하고 같은 설정으로 다시 기동합니다.

```bash
docker compose -f compose.demo.yml stop server
docker compose -f compose.demo.yml up --build server
```

두 번째 기동도 성공하고 응웬반A Worker
`92000000-0000-0000-0000-000000000006`가 한 건 유지되며, 응웬반A의 Golden Flow
Case·Task는 0건이어야 합니다. WorkerDocument는 Task·StoredFile 연결이 없는
`PASSPORT_COPY/VERIFIED` 1건과 `ARC/MISSING` 1건만 유지되어야 합니다. 다른 Showcase
Seed의 수량과 고정 ID도 첫 기동과 같아야 합니다.

종료 시 `docker compose -f compose.demo.yml down`을 사용합니다. DB 데이터를 지우려는
경우에만 정확한 Compose project와 전용 volume인지 확인한 뒤 별도로 volume 삭제를
결정합니다. 구버전 Golden Flow 예약 ID 감지로 기동이 중단된 개인 Demo DB만 초기화
대상이며, Seed가 기존 데이터를 자동 삭제하거나 Flyway로 정리하지 않습니다.

## 배포 후 Smoke

1. `deployment/server` rollout 완료
2. `/actuator/health/readiness`가 `UP`
3. 공개 `/health`가 `OK`
4. 로그인과 타 사업장 접근 차단 확인
5. `POST /api/v1/ai-runs`의 실제 Server→AI 왕복 확인
6. 후보 채택 후 Case·Task 조회 확인
7. Worker Link 대표 흐름 확인
8. SMTP가 활성화된 환경에서는 재설정 메일 수신·링크 token·새 비밀번호 로그인 확인

Runtime 장애 테스트에서는 가짜 AI 결과를 만들지 않고 안전한 오류 또는 수동 처리 상태로
남아야 합니다.

## 실패와 Rollback

진단:

```bash
kubectl -n fowoco get pods -o wide
kubectl -n fowoco describe deployment/server
kubectl -n fowoco logs deployment/server --tail=150 --all-containers
kubectl -n fowoco get events --sort-by=.lastTimestamp
```

애플리케이션 문제이며 DB migration이 이전 이미지와 호환될 때만 승인 후 이전 image SHA로
되돌립니다.

```bash
kubectl -n fowoco set image deployment/server server=ghcr.io/fowoco/server:<previous-sha>
kubectl -n fowoco rollout status deployment/server --timeout=180s
```

적용된 Flyway 파일을 수정하거나 `flyway repair`, schema history 조작, DB rollback으로
숨기지 않습니다. DB 문제는 새 forward-only migration으로 복구합니다.
