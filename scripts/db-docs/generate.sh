#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_ROOT="${REPOSITORY_ROOT}/build/db-docs"
MIGRATION_DIRECTORY="${REPOSITORY_ROOT}/src/main/resources/db/migration"

FLYWAY_IMAGE="${DB_DOCS_FLYWAY_IMAGE:-flyway/flyway:12.4.0}"
SCHEMASPY_IMAGE="${DB_DOCS_SCHEMASPY_IMAGE:-schemaspy/schemaspy:7.0.2}"
DATABASE_HOST="${DB_DOCS_CONTAINER_HOST:-127.0.0.1}"
DATABASE_PORT="${DB_DOCS_PORT:-5432}"
DATABASE_NAME="${DB_DOCS_DATABASE:-fowoco_docs}"
DATABASE_USER="${DB_DOCS_USER:-fowoco_docs}"
: "${DB_DOCS_PASSWORD:?DB_DOCS_PASSWORD를 설정해 주세요. 실제 운영 DB 비밀번호를 사용하면 안 됩니다.}"

if [[ "${DB_DOCS_EPHEMERAL:-false}" != "true" ]]; then
  echo "[db-docs] 일회용 DB 확인값(DB_DOCS_EPHEMERAL=true)이 필요합니다." >&2
  exit 1
fi
if [[ "${DATABASE_NAME}" != "fowoco_docs" || "${DATABASE_USER}" != "fowoco_docs" ]]; then
  echo "[db-docs] 문서 생성 전용 DB와 사용자(fowoco_docs)만 사용할 수 있습니다." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[db-docs] Docker를 찾지 못했습니다. Docker Desktop 또는 Docker Engine이 필요합니다." >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "[db-docs] Node.js를 찾지 못했습니다. Node.js 24 이상을 설치해 주세요." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "[db-docs] Docker가 실행 중이 아닙니다." >&2
  exit 1
fi

NETWORK_ARGUMENTS=()
if [[ -n "${DB_DOCS_DOCKER_NETWORK:-}" ]]; then
  case "${DB_DOCS_DOCKER_NETWORK}" in
    fowoco-db-docs-*) ;;
    *)
      echo "[db-docs] 문서 생성 전용 Docker network만 사용할 수 있습니다." >&2
      exit 1
      ;;
  esac
  NETWORK_ARGUMENTS=(--network "${DB_DOCS_DOCKER_NETWORK}")
elif [[ "$(uname -s)" == "Linux" ]]; then
  case "${DATABASE_HOST}" in
    127.0.0.1|localhost) ;;
    *)
      echo "[db-docs] Host network에서는 localhost DB만 사용할 수 있습니다." >&2
      exit 1
      ;;
  esac
  NETWORK_ARGUMENTS=(--network host)
else
  case "${DATABASE_HOST}" in
    127.0.0.1|localhost) DATABASE_HOST="host.docker.internal" ;;
    host.docker.internal) ;;
    *)
      echo "[db-docs] Docker Desktop에서는 host.docker.internal DB만 사용할 수 있습니다." >&2
      exit 1
      ;;
  esac
fi

case "${OUTPUT_ROOT}" in
  "${REPOSITORY_ROOT}/build/db-docs") ;;
  *)
    echo "[db-docs] 허용되지 않은 출력 경로입니다: ${OUTPUT_ROOT}" >&2
    exit 1
    ;;
esac

rm -rf "${OUTPUT_ROOT}"
mkdir -p "${OUTPUT_ROOT}/site/schema"
chmod 0777 "${OUTPUT_ROOT}/site/schema"

JDBC_URL="jdbc:postgresql://${DATABASE_HOST}:${DATABASE_PORT}/${DATABASE_NAME}"
FLYWAY_ARGUMENTS=(
  "-url=${JDBC_URL}"
  "-user=${DATABASE_USER}"
  "-password=${DB_DOCS_PASSWORD}"
  "-locations=filesystem:/flyway/sql"
  "-connectRetries=20"
)

echo "[db-docs] 빈 PostgreSQL에 Flyway Migration을 적용합니다."
docker run --rm \
  "${NETWORK_ARGUMENTS[@]}" \
  -v "${MIGRATION_DIRECTORY}:/flyway/sql:ro" \
  "${FLYWAY_IMAGE}" \
  "${FLYWAY_ARGUMENTS[@]}" \
  migrate

echo "[db-docs] 적용된 Migration과 저장소 checksum을 검증합니다."
docker run --rm \
  "${NETWORK_ARGUMENTS[@]}" \
  -v "${MIGRATION_DIRECTORY}:/flyway/sql:ro" \
  "${FLYWAY_IMAGE}" \
  "${FLYWAY_ARGUMENTS[@]}" \
  validate

docker run --rm \
  "${NETWORK_ARGUMENTS[@]}" \
  -v "${MIGRATION_DIRECTORY}:/flyway/sql:ro" \
  "${FLYWAY_IMAGE}" \
  "${FLYWAY_ARGUMENTS[@]}" \
  -outputType=json \
  info > "${OUTPUT_ROOT}/flyway-info.json"

echo "[db-docs] SchemaSpy로 테이블·관계 문서를 생성합니다."
docker run --rm \
  "${NETWORK_ARGUMENTS[@]}" \
  --user "$(id -u):$(id -g)" \
  -v "${OUTPUT_ROOT}/site/schema:/output" \
  "${SCHEMASPY_IMAGE}" \
  -t pgsql11 \
  -host "${DATABASE_HOST}" \
  -port "${DATABASE_PORT}" \
  -db "${DATABASE_NAME}" \
  -u "${DATABASE_USER}" \
  -p "${DB_DOCS_PASSWORD}" \
  -s public \
  -o /output

chmod -R a+rX "${OUTPUT_ROOT}"

GIT_COMMIT="${GITHUB_SHA:-$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)}"
if [[ -n "${GITHUB_SERVER_URL:-}" && -n "${GITHUB_REPOSITORY:-}" ]]; then
  REPOSITORY_URL="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}"
else
  REPOSITORY_URL="https://github.com/fowoco/server"
fi

node "${SCRIPT_DIR}/generate-site.mjs" \
  --flyway-info "${OUTPUT_ROOT}/flyway-info.json" \
  --output "${OUTPUT_ROOT}/site" \
  --commit "${GIT_COMMIT}" \
  --generated-at "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
  --repository-url "${REPOSITORY_URL}"

echo "[db-docs] 생성 완료: ${OUTPUT_ROOT}/site/index.html"
