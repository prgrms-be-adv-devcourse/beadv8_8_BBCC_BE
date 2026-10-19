#!/usr/bin/env bash
# 한 줄짜리 Secret 을 환경 변수에서 이름으로 읽어 .env 에 붙일 KEY='값' 줄을 출력한다. deploy.yml · monitoring-deploy.yml 이 쓴다
#   사용법: env-lines.sh <환경 이름(안내 메시지용)> <KEY>...
# compose · bash 가 $ 같은 문자를 바꾸지 않도록 작은따옴표로 감싼다. 그래서 값에 작은따옴표나 줄바꿈이 있으면 안 된다
# 검사에 걸리면 stdout 에는 아무것도 출력하지 않고 실패한다. 안내(::error::)는 stderr 로 낸다 (stdout 은 호출하는 쪽이 받아 쓴다)
set -euo pipefail
env_name="$1"
shift

# 빠진 Secret 을 한 번에 다 보여 주고 실패한다
missing=0
for key in "$@"; do
  [[ -n "${!key:-}" ]] || { echo "::error::Environment $env_name 에 Secret $key 가 없습니다" >&2; missing=1; }
done
(( missing == 0 )) || exit 1

for key in "$@"; do
  [[ "${!key}" != *[\'$'\n']* ]] || { echo "::error::$key 에 작은따옴표나 줄바꿈이 있습니다" >&2; exit 1; }
done

for key in "$@"; do
  printf "%s='%s'\n" "$key" "${!key}"
done
