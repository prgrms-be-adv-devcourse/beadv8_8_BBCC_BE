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

# 1. Flyway 실패: 재시작을 반복해도 마지막 시도의 Caused by 블록 3개만, 스택 프레임 없이
cat > "$work/log" <<EOF
 app Pulled
::group::앱 로그 (마지막 1000줄)
$start
$run_failed
java.lang.IllegalStateException: 이전 시도
	at com.example.Old.run(Old.java:1)
$start
$run_failed
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'flywayInitializer': Migration V6__order_idempotency_key.sql failed
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1800)
Caused by: org.flywaydb.core.internal.command.DbMigrate\$FlywayMigrateSqlException: Migration V6__order_idempotency_key.sql failed
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

# 2. Spring FailureAnalyzer 안내가 있으면 그것만
cat > "$work/log" <<EOF
::group::앱 로그 (마지막 1000줄)
$start
$ts ERROR [N/A] 1 --- [           main] o.s.b.d.LoggingFailureAnalysisReporter   : 

***************************
APPLICATION FAILED TO START
***************************

Description:

Failed to bind properties under 'kidly.alert.slack.webhook-url' to java.net.URI

Action:

Update your application's configuration
::endgroup::
EOF
cat > "$work/expected" <<'EOF'
Description:

Failed to bind properties under 'kidly.alert.slack.webhook-url' to java.net.URI

Action:

Update your application's configuration
EOF
check "APPLICATION FAILED TO START"

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

exit "$failed"
