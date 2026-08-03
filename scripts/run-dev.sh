#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
server_dir="$(cd "${script_dir}/.." && pwd)"
environment_file="${server_dir}/.env.local"

if [[ ! -f "${environment_file}" ]]; then
    echo ".env.local이 없습니다." >&2
    echo "cp .env.example .env.local 후 로컬 설정값을 입력하세요." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "${environment_file}"
set +a

export SPRING_PROFILES_ACTIVE=dev
: "${DB_URL:=jdbc:postgresql://localhost:5432/fowoco_test}"
: "${DB_MIGRATION_USERNAME:=postgres}"
: "${DB_RUNTIME_USERNAME:=postgres}"
export DB_URL DB_MIGRATION_USERNAME DB_RUNTIME_USERNAME

required_variables=(
    DB_MIGRATION_PASSWORD
    DB_RUNTIME_PASSWORD
    JWT_SECRET_BASE64
    DEMO_SEED_ADMIN_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
    if [[ -z "${!variable_name:-}" ]]; then
        echo "${variable_name} 환경변수가 필요합니다. .env.local을 확인하세요." >&2
        exit 1
    fi
done

cd "${server_dir}"
exec ./gradlew bootRun --args="--app.demo-seed.enabled=true"
