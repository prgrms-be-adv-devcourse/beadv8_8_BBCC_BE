#!/usr/bin/env bash
# startup-error.sh 가 케이스마다 기대한 원인을 뽑는지 확인한다. 실행: .github/scripts/startup-error-test.sh
set -euo pipefail
script="$(dirname "$0")/startup-error.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failed=0

check() {
  local name="$1"
  if diff -u "$work/expected" <("$script" "$work/log"); then
    echo "ok   $name"
  else
    echo "FAIL $name"
    failed=1
  fi
}

ts='2026-10-16 14:57:40.000'
start="$ts  INFO [N/A] 1 --- [           main] com.bbcc.kidly.KidlyApplication          : Starting KidlyApplication using Java 25"
run_failed="$ts ERROR [N/A] 1 --- [           main] o.s.boot.SpringApplication               : Application run failed"

# 서버 콘솔(prod)의 ECS JSON 한 줄. prod 프로필 기동 실패 때 실제로 찍힌 모양을 따른다
# 사용: ecs 레벨 logger 메시지 [스택 트레이스]
ecs() {
  jq -nc --arg level "$1" --arg logger "$2" --arg message "$3" --arg stack "${4:-}" '
    {"@timestamp": "2026-10-16T05:57:40.000000Z", log: {level: $level, logger: $logger},
     process: {pid: 1, thread: {name: "main"}}, service: {name: "kidly", version: "0.0.1-SNAPSHOT", node: {}},
     message: $message}
    + (if $stack == "" then {} else
        {error: {type: ($stack | split(": ")[0]), message: ($stack | split("\n")[0] | sub("^[^:]+: "; "")), stack_trace: $stack}} end)
    + {tags: ["COMMONS-LOGGING"], ecs: {version: "8.11"}}'
}
json_start=$(ecs INFO com.bbcc.kidly.KidlyApplication "Starting KidlyApplication v0.0.1-SNAPSHOT using Java 25.0.4")
json_run_failed() {
  ecs ERROR org.springframework.boot.SpringApplication "Application run failed" "$1"
}

# 1. Flyway 실패: 재시작을 반복해도 마지막 시도의 Caused by 블록 3개만, 스택 프레임 없이 (텍스트 · JSON)
cat > "$work/flyway_stack" <<'EOF'
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'flywayInitializer': Migration V6__order_idempotency_key.sql failed
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1800)
Caused by: org.flywaydb.core.internal.command.DbMigrate$FlywayMigrateSqlException: Migration V6__order_idempotency_key.sql failed
-------------------------------------------
SQL State  : 42P07
Message    : ERROR: relation "order_idempotency_key" already exists
Location   : db/migration/V6__order_idempotency_key.sql
Line       : 1
Statement  : CREATE TABLE order_idempotency_key (id bigint)

	at org.flywaydb.core.internal.command.DbMigrate.doMigrateGroup(DbMigrate.java:371)
	... 20 common frames omitted
Caused by: org.postgresql.util.PSQLException: ERROR: relation "order_idempotency_key" already exists
	at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2733)
	... 30 common frames omitted
EOF
flyway_stack=$(cat "$work/flyway_stack")
cat > "$work/log" <<EOF
 app Pulled
::group::앱 로그 (마지막 1000줄)
$start
$run_failed
java.lang.IllegalStateException: 이전 시도
	at com.example.Old.run(Old.java:1)
$start
$run_failed
$flyway_stack
::endgroup::
EOF
cat > "$work/expected" <<'EOF'
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'flywayInitializer': Migration V6__order_idempotency_key.sql failed
Caused by: org.flywaydb.core.internal.command.DbMigrate$FlywayMigrateSqlException: Migration V6__order_idempotency_key.sql failed
-------------------------------------------
SQL State  : 42P07
Message    : ERROR: relation "order_idempotency_key" already exists
Location   : db/migration/V6__order_idempotency_key.sql
Line       : 1
Statement  : CREATE TABLE order_idempotency_key (id bigint)

Caused by: org.postgresql.util.PSQLException: ERROR: relation "order_idempotency_key" already exists
EOF
check "Flyway 실패"
{
  echo "::group::앱 로그 (마지막 1000줄)"
  echo " :: Spring Boot ::                (v4.1.1)"
  echo "$json_start"
  json_run_failed $'java.lang.IllegalStateException: 이전 시도\n\tat com.example.Old.run(Old.java:1)\n'
  echo "$json_start"
  json_run_failed "$flyway_stack"$'\n'
  echo "::endgroup::"
} > "$work/log"
check "JSON Flyway 실패"

# 2. Spring FailureAnalyzer 안내가 있으면 그것만 (텍스트 · JSON)
cat > "$work/analysis" <<'EOF'
***************************
APPLICATION FAILED TO START
***************************

Description:

Failed to bind properties under 'kidly.alert.slack.webhook-url' to java.net.URI

Action:

Update your application's configuration
EOF
analysis=$(cat "$work/analysis")
cat > "$work/log" <<EOF
::group::앱 로그 (마지막 1000줄)
$start
$ts ERROR [N/A] 1 --- [           main] o.s.b.d.LoggingFailureAnalysisReporter   : 

$analysis
::endgroup::
EOF
cat > "$work/expected" <<'EOF'
Description:

Failed to bind properties under 'kidly.alert.slack.webhook-url' to java.net.URI

Action:

Update your application's configuration
EOF
check "APPLICATION FAILED TO START"
{
  echo "::group::앱 로그 (마지막 1000줄)"
  echo "$json_start"
  ecs ERROR org.springframework.boot.diagnostics.LoggingFailureAnalysisReporter $'\n\n'"$analysis"$'\n'
  echo "::endgroup::"
} > "$work/log"
check "JSON APPLICATION FAILED TO START"

# 3. 알 수 없는 실패면 마지막 40줄
{
  echo "::group::앱 로그 (마지막 1000줄)"
  for i in $(seq 1 45); do echo "line $i"; done
  echo "::endgroup::"
} > "$work/log"
seq 6 45 | sed 's/^/line /' > "$work/expected"
check "알 수 없는 실패"

# 4. 앱 로그 구간이 없으면(이미지 pull 실패 등) 출력 없음
printf 'Error response from daemon: manifest unknown\n' > "$work/log"
: > "$work/expected"
check "앱 로그 없음"

# 5. 2,500자를 넘으면 앞을 자르고 표시
{
  echo "::group::앱 로그 (마지막 1000줄)"
  echo "$run_failed"
  printf 'java.lang.IllegalStateException: %s\n' "$(printf 'a%.0s' $(seq 1 3000))"
  echo "::endgroup::"
} > "$work/log"
{ printf '…(앞부분 생략)\n'; printf 'a%.0s' $(seq 1 2500); printf '\n'; } > "$work/expected"
check "긴 원인 자르기"

# 6. 마지막 시도가 아직 기동 중이면(실패 표시 없음) 실패 표시가 있는 앞 시도를 쓴다
cat > "$work/log" <<EOF
::group::앱 로그 (마지막 1000줄)
$start
$run_failed
java.lang.IllegalStateException: boom
	at com.example.Boom.run(Boom.java:1)
$start
$ts  INFO [N/A] 1 --- [           main] com.bbcc.kidly.KidlyApplication          : The following 1 profile is active: "prod"
$ts  INFO [N/A] 1 --- [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories
::endgroup::
EOF
printf 'java.lang.IllegalStateException: boom\n' > "$work/expected"
check "기동 중에 끊긴 마지막 시도"
{
  echo "::group::앱 로그 (마지막 1000줄)"
  echo "$json_start"
  json_run_failed $'java.lang.IllegalStateException: boom\n\tat com.example.Boom.run(Boom.java:1)\n'
  echo "$json_start"
  ecs INFO com.bbcc.kidly.KidlyApplication 'The following 1 profile is active: "prod"'
  ecs INFO org.springframework.data.repository.config.RepositoryConfigurationDelegate \
    "Bootstrapping Spring Data JPA repositories"
  echo "::endgroup::"
} > "$work/log"
check "JSON 기동 중에 끊긴 마지막 시도"

exit "$failed"
