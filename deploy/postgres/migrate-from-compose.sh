#!/usr/bin/env bash
# 한 번만 쓰는 이전 스크립트. t3.large compose 의 postgres(<환경>-postgres-1, database kidly) 데이터를 t3.medium k3s 의 kidly_<환경> 으로 옮긴다
# 운영자 PC 에서 레포 루트로 실행한다. 두 서버에 SSH 로 접속할 수 있어야 한다
#   APP_ENV=dev APP_SSH="-i ~/.ssh/<키> ec2-user@<t3.large IP>" INFRA_SSH="-i ~/.ssh/<키> ec2-user@<t3.medium IP>" deploy/postgres/migrate-from-compose.sh
# 1) medium 에 인스턴스 · 그 환경 계정을 준비한다(deploy.sh, 비밀번호는 t3.large 의 /opt/app/<환경>/.env 에서 화면에 내지 않고 넘긴다)
# 2) 원본을 덤프해 앱 계정으로 복원한다. recommend 스키마 · vector 확장은 부트스트랩이 만들었으므로 덤프 목록에서 뺀다
# 3) 테이블별 행 수를 원본과 비교한다
# 대상 database 에 테이블이 하나라도 있으면 멈춘다(두 번 옮기지 않는다). 덤프 뒤 원본에 들어온 쓰기는 따라오지 않으므로 이 PR 을 머지하기 직전에 돌린다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}" "${APP_SSH:?APP_SSH 가 필요합니다}" "${INFRA_SSH:?INFRA_SSH 가 필요합니다}"
[[ "$APP_ENV" == dev || "$APP_ENV" == prod ]] || { echo "Invalid APP_ENV"; exit 1; }
cd "$(dirname "$0")"

db="kidly_$APP_ENV"
src="$APP_ENV-postgres-1"
# shellcheck disable=SC2086,SC2029
app() { ssh $APP_SSH "$@"; }
# shellcheck disable=SC2086,SC2029
infra() { ssh $INFRA_SSH "$@"; }
pod_psql() { infra "sudo k3s kubectl -n postgres exec -i deploy/postgres -- psql -v ON_ERROR_STOP=1 -U postgres -d $db -tA"; }
src_psql() { app "sudo docker exec -i $src psql -v ON_ERROR_STOP=1 -U kidly -d kidly -tA"; }

dir="/opt/app/postgres-$APP_ENV"
tar -czf - postgres.yaml deploy.sh | infra "sudo mkdir -p $dir && sudo tar -xzf - --no-same-owner -C $dir && sudo chown -R deploy:deploy $dir"
app "sudo sed -n \"s/^DB_PASSWORD='\\(.*\\)'\$/\\1/p\" /opt/app/$APP_ENV/.env" \
    | infra "sudo -iu deploy env APP_ENV=$APP_ENV $dir/deploy.sh"

tables="select count(*) from information_schema.tables where table_schema not in ('pg_catalog', 'information_schema')"
[[ "$(pod_psql <<< "$tables")" == 0 ]] || { echo "$db 에 이미 테이블이 있어 멈춥니다"; exit 1; }

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
app "sudo docker exec $src pg_dump -U kidly -d kidly -Fc --no-owner --no-privileges" > "$tmp"
infra "sudo k3s kubectl -n postgres exec -i deploy/postgres -- sh -c 'cat > /tmp/migrate.dump'" < "$tmp"
infra "sudo k3s kubectl -n postgres exec -i deploy/postgres -- sh -s $db" <<'SH'
set -eu
pg_restore -l /tmp/migrate.dump | grep -vE ' SCHEMA - recommend | EXTENSION - vector| COMMENT - EXTENSION vector' > /tmp/migrate.list
pg_restore -U postgres -d "$1" -L /tmp/migrate.list --no-owner --role="$1" --exit-on-error /tmp/migrate.dump
rm -f /tmp/migrate.dump /tmp/migrate.list
SH

counts="select string_agg(t, ' ' order by t) from (
  select table_schema || '.' || table_name || '=' ||
         (xpath('/row/c/text()', query_to_xml('select count(*) as c from ' || quote_ident(table_schema) || '.' || quote_ident(table_name), false, true, '')))[1]::text as t
  from information_schema.tables
  where table_schema not in ('pg_catalog', 'information_schema') and table_type = 'BASE TABLE') x"
before=$(src_psql <<< "$counts")
after=$(pod_psql <<< "$counts")
if [[ "$before" == "$after" ]]; then
    echo "이전 완료: 테이블 $(wc -w <<< "$after")개의 행 수가 원본과 같습니다"
else
    echo "행 수가 다릅니다"; diff <(tr ' ' '\n' <<< "$before") <(tr ' ' '\n' <<< "$after"); exit 1
fi
