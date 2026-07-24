#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_ID="$$"
NETWORK_NAME="fowoco-db-docs-${RUN_ID}"
CONTAINER_NAME="fowoco-db-docs-postgres-${RUN_ID}"
POSTGRES_IMAGE="${DB_DOCS_POSTGRES_IMAGE:-postgres:17-alpine}"
DATABASE_NAME="fowoco_docs"
DATABASE_USER="fowoco_docs"
DATABASE_PASSWORD="fowoco_docs_local_only"

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "[db-docs] 실행 중인 Docker Desktop 또는 Docker Engine이 필요합니다." >&2
  exit 1
fi

cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker network create "${NETWORK_NAME}" >/dev/null
docker run -d --rm \
  --name "${CONTAINER_NAME}" \
  --network "${NETWORK_NAME}" \
  -e "POSTGRES_DB=${DATABASE_NAME}" \
  -e "POSTGRES_USER=${DATABASE_USER}" \
  -e "POSTGRES_PASSWORD=${DATABASE_PASSWORD}" \
  "${POSTGRES_IMAGE}" >/dev/null

for attempt in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" pg_isready -U "${DATABASE_USER}" -d "${DATABASE_NAME}" >/dev/null 2>&1; then
    break
  fi
  if [[ "${attempt}" == "30" ]]; then
    echo "[db-docs] PostgreSQL이 준비되지 않았습니다." >&2
    exit 1
  fi
  sleep 1
done

DB_DOCS_DOCKER_NETWORK="${NETWORK_NAME}" \
DB_DOCS_CONTAINER_HOST="${CONTAINER_NAME}" \
DB_DOCS_EPHEMERAL=true \
DB_DOCS_DATABASE="${DATABASE_NAME}" \
DB_DOCS_USER="${DATABASE_USER}" \
DB_DOCS_PASSWORD="${DATABASE_PASSWORD}" \
"${SCRIPT_DIR}/generate.sh"
