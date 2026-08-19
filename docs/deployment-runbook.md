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
| PII | `PII_ENCRYPTION_ENABLED=true` | 계정 연락처 AES-256-GCM 암호화 활성화 |
| PII | `PII_ENCRYPTION_KEY_BASE64` | 현재 32바이트 연락처 암호화 키의 Base64 |
| PII | `PII_ENCRYPTION_KEY_VERSION` | 현재 키 식별 version, 예: `pii-2026-08-v1` |
| PII | `PII_DECRYPTION_KEYS` | 회전 이전 키 목록, `version=base64`를 쉼표로 구분 |
| PII | `PII_MAINTENANCE_COMMAND=none` | 일반 Server의 고정값. 일회성 작업에서만 변경 |
| PII | `PII_MAINTENANCE_BATCH_SIZE=100` | 연락처 전환 작업의 transaction당 처리 건수 |
| Web | `CORS_ALLOWED_ORIGINS` | 실제 Client HTTPS origin만 허용 |
| Catalog | `WORKFLOW_CATALOG_LOCATION` | 검증된 `RELEASED` projection 위치 |
| AI | `AI_RUNTIME_ENABLED=true` | 실제 Runtime 연동 활성화 |
| AI | `AI_RUNTIME_ENDPOINT` | 예: `http://ai:8000/internal/v1/analyses` |
| AI | `AI_RUNTIME_SERVICE_CREDENTIAL` | Server↔AI 내부 Bearer credential |
| Worker Link | `WORKER_PORTAL_BASE_URL` | 문자에 넣을 실제 Client HTTPS 주소 |
| Worker Link | `WORKER_LINK_SMS_PROVIDER=solapi` | SMS Adapter 활성화 |
| Worker Link | `SOLAPI_API_KEY`, `SOLAPI_API_SECRET` | SMS Provider credential |
| Worker Link | `SOLAPI_SENDER_NUMBER` | Provider에 등록·승인된 발신번호 |
| OCR | `AI_OCR_ENABLED=true`, `DOCUMENT_OCR_ENABLED=true` | AI OCR 호출과 Server 저장 기능 활성화 |
| OCR | `AI_OCR_ENDPOINT`, `AI_OCR_SERVICE_CREDENTIAL` | OCR 내부 endpoint와 Bearer credential |
| OCR | `OCR_RESULT_ENCRYPTION_KEY_BASE64` | 32바이트 OCR 결과 암호화 키의 Base64 |
| OCR | `OCR_RESULT_KEY_VERSION` | 암호화 키 식별 version |

`PII_ENCRYPTION_KEY_BASE64`와 `PII_DECRYPTION_KEYS`는 Git, DB, 이미지, Issue, 로그에
기록하지 않고 `server-env` Secret으로 주입합니다. 운영에서는 `prod` profile이 PII 암호화를
기본 활성화하므로 현재 키가 없으면 Server가 기동하지 않습니다. 현재 Infra는 Kubernetes
Secret 주입까지 지원하며 AWS KMS·Secrets Manager 자동 동기화는 별도 고도화 범위입니다.

로그인·프로필 수정 시 평문 또는 이전 키 암호문을 현재 키로 다시 암호화하는 방어 로직이
있지만, 계정 접근 빈도에 의존하는 점진 전환을 배포 완료 기준으로 사용하지 않습니다. 초기
전환과 키 회전은 아래 일회성 유지보수 명령으로 모든 행을 명시적으로 처리합니다.

| 명령 | 목적 | 완료 조건 |
| --- | --- | --- |
| `migrate` | 평문과 이전 키 암호문을 현재 키로 전환 | 처리 후 오류 없이 종료 |
| `verify` | 전환 완료 여부 검사 | 평문 0건, 이전 키 0건 |
| `restore-plaintext` | 구버전 애플리케이션 롤백 전 평문 복원 | 암호문 0건 |

유지보수 명령은 PostgreSQL RLS를 우회해야 하므로 일반 Runtime 계정으로 실행되지 않습니다.
`user_account` 소유자, `BYPASSRLS` 또는 Superuser 권한을 가진 **Flyway 전용 계정**을 일회성
프로세스에만 주입합니다. 정상 Deployment의 `PII_MAINTENANCE_COMMAND`는 항상 `none`입니다.

### 최초 암호화 전환

1. DB 백업과 복구 절차를 확인합니다.
2. 새 컬럼과 암호화 코드를 먼저 배포하고 현재 키를 Secret으로 주입합니다.
3. 쓰기 트래픽을 통제한 뒤 일회성 프로세스에서 `migrate`를 실행합니다.
4. 같은 키로 `verify`를 실행해 평문과 이전 키 잔여 건수가 0인지 확인합니다.
5. 정상 Server의 로그인·프로필 조회 Smoke를 수행합니다.

```bash
export SPRING_MAIN_WEB_APPLICATION_TYPE=none
export PII_MAINTENANCE_COMMAND=migrate
export PII_MAINTENANCE_BATCH_SIZE=100
export DB_RUNTIME_USERNAME="$DB_MIGRATION_USERNAME"
export DB_RUNTIME_PASSWORD="$DB_MIGRATION_PASSWORD"
java -jar server.jar

export PII_MAINTENANCE_COMMAND=verify
java -jar server.jar
```

Kubernetes에서는 동일 환경변수를 가진 일회성 Job으로 실행합니다. 일반 Deployment의 Secret을
`migrate`로 바꾸지 않으며, 로그에는 원문·암호문 대신 처리 건수와 키 version만 남습니다.

### 키 회전

1. 새 키와 새 version을 현재 값으로 설정합니다.
2. 직전 키를 `PII_DECRYPTION_KEYS=old-version=old-base64`에 유지합니다.
3. 새 설정을 배포한 뒤 `migrate`, `verify`를 순서대로 실행합니다.
4. DB와 애플리케이션 Smoke를 확인한 뒤 이전 version 잔여 건수가 0일 때만 이전 키를 제거합니다.

### 구버전 애플리케이션 롤백

암호화 도입 이전 버전은 `phone_ciphertext`를 읽지 못하므로 이미지를 먼저 되돌리면 연락처가
빈 값으로 보입니다. 반드시 모든 복호화 키를 유지한 상태에서 쓰기 트래픽을 통제하고
`restore-plaintext`를 먼저 실행합니다. 아래 조회에서 암호문 0건을 확인한 다음에만 구버전
이미지를 배포합니다. 장애 수정 후에는 다시 `migrate`, `verify`를 수행하는 전진 복구를
우선합니다.

```sql
SELECT COUNT(*) AS legacy_plaintext_phone_count
FROM user_account
WHERE phone IS NOT NULL;

SELECT phone_key_version, COUNT(*) AS encrypted_phone_count
FROM user_account
WHERE phone_ciphertext IS NOT NULL
GROUP BY phone_key_version
ORDER BY phone_key_version;

SELECT COUNT(*) AS remaining_encrypted_phone_count
FROM user_account
WHERE phone_ciphertext IS NOT NULL;
```

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

## 관측 설정 경계

Server는 AiRun·Renewal 구간의 Micrometer 지표를 생성하지만, 현재 데모 배포에서는
Prometheus를 클러스터에 함께 배포하지 않습니다.

- `/actuator/prometheus`는 기본 보안 Chain에서 보호됩니다.
- 로컬 `observability` profile은 `prod`와 함께 활성화해도 공개 Chain이 생성되지
  않습니다.
- 배포 환경에서 수집이 필요해지면 Infra가 내부 Service·NetworkPolicy·인증 또는
  별도 management port를 먼저 구성합니다.
- 공개 Ingress와 `CORS_ALLOWED_ORIGINS`에 Prometheus endpoint를 추가하지 않습니다.
- Metric에는 `companyId`, `workerId`, `taskId`, 요청·시도 ID와 개인정보를 tag로
  넣지 않습니다.

따라서 현재 `server-env`에 `SPRING_PROFILES_ACTIVE=prod,observability`를 설정하면
안 됩니다. 로컬 측정과 정량 평가 절차는
[AI 파이프라인 관측 가이드](ai-pipeline-observability.md)를 사용합니다.

현재 Infra에 HTTPS/TLS와 `RELEASED` Workflow Catalog 배포가 없으면 `prod` 완료 조건을
충족하지 못합니다. 임시 HTTP 주소와 DRAFT Catalog는 개발 Smoke에만 사용합니다.

## HTTPS와 공유 Swagger 연결

GitHub Pages의 공유 Swagger는 HTTPS 페이지이므로 HTTP 데모 Server를 직접 호출할 수
없습니다. Infra에서 TLS가 준비된 뒤 다음 순서로 연결합니다.

1. Infra Ingress에 TLS 인증서와 HTTPS host를 적용합니다.
2. Server `CORS_ALLOWED_ORIGINS`에 실제 Client origin과
   `https://fowoco.github.io`를 쉼표로 구분해 등록합니다.
3. Server 저장소 Actions Variable `SERVER_PUBLIC_URL`에 HTTPS Server 주소를 등록합니다.
4. `Database Documentation` Workflow를 재실행합니다.
5. 공유 Swagger에서 Login → Authorize → 보호 API 호출을 확인합니다.

현재 Infra가 HTTP만 제공하는 동안에는 `SERVER_PUBLIC_URL`을 등록하지 않고 공유
Swagger를 읽기 전용으로 유지합니다. HTTP 주소를 임시로 넣어 브라우저 보안을 우회하지
않습니다.

## 로컬 PostgreSQL 통합 실행

아래 Compose는 로컬 개발용이며 운영 Secret이나 실제 개인정보를 사용하지 않습니다.

```bash
export DEMO_DB_PASSWORD='local-demo-password'
export JWT_SECRET_BASE64="$(openssl rand -base64 32)"
export PII_ENCRYPTION_ENABLED=true
export PII_ENCRYPTION_KEY_BASE64="$(openssl rand -base64 32)"
export PII_ENCRYPTION_KEY_VERSION='local-pii-v1'
export DEMO_SEED_ENABLED=true
export DEMO_SEED_ADMIN_PASSWORD='로컬 전용 12자 이상 값'
docker compose -f compose.demo.yml up --build
```

`DEMO_SEED_ENABLED`의 Compose 기본값은 안전하게 `false`입니다. 개인 Demo DB에서 Seed가
필요한 경우에만 위와 같이 활성화하고 12자 이상의 합성 비밀번호를 지정합니다. 첫 기동은
빈 PostgreSQL 16 DB에 Flyway와 전체 Demo Seed를 적용합니다.

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
7. Worker Link 대표 흐름과 동일 `Idempotency-Key` 문서 재시도가 같은 `upload_id`로
   수렴하는지 확인
8. SMS가 활성화된 환경에서는 실제 수신·링크 접속·중복 발송 방지 확인
9. SMTP가 활성화된 환경에서는 재설정 메일 수신·링크 token·새 비밀번호 로그인 확인
10. 로그에서 AiRun·Renewal `TOTAL` 단계와 안전한 `failure_code`가 기록되는지 확인
11. 실제 `FILE_STORAGE_LOCAL_PATH` volume에 최종 파일만 한 개 있고
    `.fowoco-upload-*.tmp`가 남지 않았는지 확인

파일 rollback cleanup 실패나 transaction `UNKNOWN` 로그가 있으면
[File Storage rollback 보상 운영 가이드](reliability/file-storage-rollback-compensation.md)의
DB·volume 대조 절차를 따른다.

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

Worker Link 문서 멱등성 V51을 적용한 뒤 이전 Server image로 되돌릴 때는 schema 호환과
멱등성 의미 호환을 구분합니다. 이전 image는 nullable hash column을 무시하고 기동할 수
있지만, 새 version이 `canonical:<stored_file_id>`로 기록한 성공 결과를 기존
`clientRequestId` 조회로 재사용하지 못합니다. rollback 가능한 기간에는 Client가
`Idempotency-Key`와 multipart `clientRequestId`를 함께 보내는 동작을 유지하고, rollback
후에는 새 version에서 성공한 문서 업로드를 자동 재시도하지 않습니다. 상세 점검은
[File Storage rollback 보상 운영 가이드](reliability/file-storage-rollback-compensation.md)의
Rollback 원칙을 따릅니다.

```bash
kubectl -n fowoco set image deployment/server server=ghcr.io/fowoco/server:<previous-sha>
kubectl -n fowoco rollout status deployment/server --timeout=180s
```

적용된 Flyway 파일을 수정하거나 `flyway repair`, schema history 조작, DB rollback으로
숨기지 않습니다. DB 문제는 새 forward-only migration으로 복구합니다.
