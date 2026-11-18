#!/usr/bin/env bash
# Validation uses --quiet so expanded deployment secrets never enter build logs.
set -Eeuo pipefail
root_dir=$(cd "$(dirname "$0")/.." && pwd)
cd "$root_dir"
docker compose config --quiet
for required in SKY_DB_PASSWORD MYSQL_ROOT_PASSWORD SKY_JWT_SECRET SKY_SCRIPT_ENV_ENCRYPTION_KEY; do
  if env -u "$required" docker compose --env-file /dev/null config --quiet 2>/dev/null; then
    echo "Missing $required should fail validation"; exit 1
  fi
  if env "$required=" docker compose --env-file /dev/null config --quiet 2>/dev/null; then
    echo "Empty $required should fail validation"; exit 1
  fi
done
echo 'Compose accepts explicit secrets and rejects missing/empty required values.'
