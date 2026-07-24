#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_ROOT="${REPOSITORY_ROOT}/build/api-docs"
APPLICATION_PORT="${API_DOCS_PORT:-18080}"
APPLICATION_PID=""

case "${OUTPUT_ROOT}" in
  "${REPOSITORY_ROOT}/build/api-docs") ;;
  *)
    echo "[api-docs] 허용되지 않은 출력 경로입니다: ${OUTPUT_ROOT}" >&2
    exit 1
    ;;
esac

for command in java node curl; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "[api-docs] ${command} 명령을 찾지 못했습니다." >&2
    exit 1
  fi
done

if curl --silent --fail "http://127.0.0.1:${APPLICATION_PORT}/health" >/dev/null 2>&1; then
  echo "[api-docs] ${APPLICATION_PORT} 포트가 이미 사용 중입니다. API_DOCS_PORT를 변경해 주세요." >&2
  exit 1
fi

cleanup() {
  if [[ -n "${APPLICATION_PID}" ]] && kill -0 "${APPLICATION_PID}" >/dev/null 2>&1; then
    kill "${APPLICATION_PID}" >/dev/null 2>&1 || true
    wait "${APPLICATION_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

rm -rf "${OUTPUT_ROOT}"
mkdir -p "${OUTPUT_ROOT}/site"

echo "[api-docs] 실행 가능한 서버 jar를 빌드합니다."
"${REPOSITORY_ROOT}/gradlew" -p "${REPOSITORY_ROOT}" bootJar

APPLICATION_JAR="$(find "${REPOSITORY_ROOT}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"
if [[ -z "${APPLICATION_JAR}" ]]; then
  echo "[api-docs] 실행 가능한 서버 jar를 찾지 못했습니다." >&2
  exit 1
fi

echo "[api-docs] 실제 Spring MVC 계약을 test profile로 생성합니다."
java -jar "${APPLICATION_JAR}" \
  --spring.profiles.active=test \
  --server.address=127.0.0.1 \
  --server.port="${APPLICATION_PORT}" \
  > "${OUTPUT_ROOT}/application.log" 2>&1 &
APPLICATION_PID="$!"

OPENAPI_TEMP="${OUTPUT_ROOT}/openapi.json.tmp"
for attempt in $(seq 1 60); do
  if curl --silent --fail \
      "http://127.0.0.1:${APPLICATION_PORT}/v3/api-docs" \
      --output "${OPENAPI_TEMP}"; then
    break
  fi
  if ! kill -0 "${APPLICATION_PID}" >/dev/null 2>&1; then
    echo "[api-docs] 서버가 준비되기 전에 종료되었습니다." >&2
    tail -n 100 "${OUTPUT_ROOT}/application.log" >&2
    exit 1
  fi
  if [[ "${attempt}" == "60" ]]; then
    echo "[api-docs] OpenAPI endpoint가 준비되지 않았습니다." >&2
    tail -n 100 "${OUTPUT_ROOT}/application.log" >&2
    exit 1
  fi
  sleep 1
done
mv "${OPENAPI_TEMP}" "${OUTPUT_ROOT}/openapi.json"

GIT_COMMIT="${API_DOCS_GIT_COMMIT:-${GITHUB_SHA:-$(git -C "${REPOSITORY_ROOT}" rev-parse HEAD)}}"
if [[ -n "${GITHUB_SERVER_URL:-}" && -n "${GITHUB_REPOSITORY:-}" ]]; then
  REPOSITORY_URL="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}"
else
  REPOSITORY_URL="https://github.com/fowoco/server"
fi

node "${SCRIPT_DIR}/generate-site.mjs" \
  --openapi "${OUTPUT_ROOT}/openapi.json" \
  --output "${OUTPUT_ROOT}/site" \
  --commit "${GIT_COMMIT}" \
  --generated-at "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
  --repository-url "${REPOSITORY_URL}"

echo "[api-docs] 생성 완료: ${OUTPUT_ROOT}/site/index.html"
