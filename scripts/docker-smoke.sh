#!/usr/bin/env bash
# Only disposable containers/volumes on a private network; never uses .env or compose.
set -Eeuo pipefail
image=${1:?Usage: docker-smoke.sh IMAGE PLUGIN_JAR}
plugin=${2:?Pass the standalone frontend integration plugin jar}
prefix="sky-smoke-${GITHUB_RUN_ID:-local}-${RANDOM}"
network="$prefix"
mysql="$prefix-mysql"
backend="$prefix-backend"
volume="$prefix-plugins"
password=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
encryption_key=$(python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())')
root_dir=$(cd "$(dirname "$0")/.." && pwd)
state=$(mktemp)
cleanup() {
  result=$?
  trap - EXIT
  if [[ $result -ne 0 ]]; then
    docker logs "$mysql" || true
    docker logs "$backend" || true
  fi
  docker rm -f -v "$backend" "$mysql" >/dev/null 2>&1 || true
  docker volume rm "$volume" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f "$state"
  exit "$result"
}
trap cleanup EXIT
docker network create "$network" >/dev/null
docker volume create "$volume" >/dev/null
docker run -d --name "$mysql" --network "$network" --network-alias mysql \
  -e TZ=Asia/Shanghai \
  -e MYSQL_DATABASE=sky_new -e MYSQL_USER=sky -e MYSQL_PASSWORD="$password" \
  -e MYSQL_ROOT_PASSWORD="$password" \
  --mount "type=bind,src=$root_dir/sql/schema-mysql.sql,dst=/docker-entrypoint-initdb.d/01-schema.sql,readonly" \
  mysql:8.4 >/dev/null
ready=false
for attempt in {1..120}; do
  if docker exec -e MYSQL_PWD="$password" "$mysql" \
      mysql --protocol=TCP -h 127.0.0.1 -usky sky_new -e 'SELECT 1' >/dev/null 2>&1; then
    ready=true; break
  fi
  sleep 2
done
[[ "$ready" == true ]]
start_backend() {
  docker run -d --name "$backend" --init --network "$network" \
    -p 127.0.0.1::8080 -v "$volume:/app/data/plugins" \
    -e "SKY_DB_URL=jdbc:mysql://mysql:3306/sky_new?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
    -e SKY_DB_USERNAME=sky -e "SKY_DB_PASSWORD=$password" \
    -e SKY_JWT_SECRET=smoke-only-secret-at-least-thirty-two-bytes \
    -e "SKY_SCRIPT_ENV_ENCRYPTION_KEY=$encryption_key" \
    "$image" >/dev/null
  ready=false
  for attempt in {1..120}; do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$backend")
    if [[ "$status" == healthy ]]; then ready=true; break; fi
    sleep 2
  done
  [[ "$ready" == true ]]
  port=$(docker port "$backend" 8080/tcp | head -n 1 | awk -F: '{print $NF}')
}
docker run --rm --entrypoint sh "$image" -c \
  'set -e; test "$(id -u)" = 10001; java -version; node --version; python3.12 --version; test "$(env -u TZ date +%z)" = +0800'
start_backend
python3 "$root_dir/scripts/deployment-smoke.py" "http://127.0.0.1:$port" "$plugin" "$state" initialize
python3 "$root_dir/scripts/script-deployment-smoke.py" "http://127.0.0.1:$port" "$state" initialize
# Verify persistence and restored scheduling after replacement with the same volume.
docker top "$backend" -eo pid,args | grep -q '/app/runner.jar'
docker stop --time 60 "$backend" >/dev/null
[[ $(docker inspect --format '{{.State.ExitCode}}' "$backend") != 137 ]]
docker rm "$backend" >/dev/null
start_backend
python3 "$root_dir/scripts/deployment-smoke.py" "http://127.0.0.1:$port" "$plugin" "$state" verify
python3 "$root_dir/scripts/script-deployment-smoke.py" "http://127.0.0.1:$port" "$state" verify
# A disconnected DB must change readiness to HTTP 503 without leaking details.
docker stop "$mysql" >/dev/null
docker exec "$backend" sh -c \
  'test "$(curl --silent --output /tmp/db-health --write-out "%{http_code}" http://127.0.0.1:8080/api/actuator/health)" = 503'
echo 'Container smoke test passed.'
