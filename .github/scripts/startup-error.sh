#!/usr/bin/env bash
# deploy.yml 배포 단계 출력에서 앱이 뜨지 못한 원인을 뽑아 출력한다. 기동 실패가 아니면 아무것도 출력하지 않는다
# deploy.sh 는 up --wait 가 실패할 때만 "::group::앱 로그" 구간에 앱 로그를 남긴다
# 컨테이너가 재시작을 반복하므로 실패 표시(Application run failed · APPLICATION FAILED TO START)가 있는
# 마지막 기동 시도(Starting ...Application 이후)만 본다. 로그를 잡은 순간 마지막 시도가 아직 기동 중이면 그 앞 시도를 쓴다
# 실패 표시가 어디에도 없으면 마지막 시도를 본다(Starting 줄이 없으면 구간 전체)
#   1) Spring 의 APPLICATION FAILED TO START 안내(Description · Action)
#   2) 없으면 "Application run failed" 뒤 예외의 마지막 블록 3개 (여러 줄 메시지 포함, 스택 프레임 제외)
#   3) 둘 다 없으면 마지막 40줄
# Slack section 한도(3,000자)에 이스케이프 몫을 남기려고 2,500자를 넘으면 앞부분을 자른다
set -euo pipefail
log_file="$1"

# 로그 줄은 날짜로 시작한다 (logback 패턴 %d{yyyy-MM-dd ...}). mawk 는 {4} 를 지원하지 않을 수 있다
log_line='^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] '

section=$(awk '
  /::group::앱 로그/ { buf = ""; on = 1; next }
  /::endgroup::/ { on = 0 }
  on { buf = buf $0 "\n" }
  END { printf "%s", buf }' "$log_file")
[ -n "$section" ] || exit 0

attempt=$(printf '%s\n' "$section" | awk '
  /Starting [A-Za-z]+Application/ { if (failed) last = buf; buf = ""; failed = 0 }
  { buf = buf $0 "\n" }
  /Application run failed|^APPLICATION FAILED TO START$/ { failed = 1 }
  END { printf "%s", (failed || last == "" ? buf : last) }')

cause=$(printf '%s\n' "$attempt" | awk -v log_line="$log_line" '
  /^APPLICATION FAILED TO START$/ { on = 1; buf = ""; next }
  on && $0 ~ log_line { on = 0 }
  on && !/^\*+$/ { buf = buf $0 "\n" }
  END { printf "%s", buf }')

if [ -z "$cause" ]; then
  cause=$(printf '%s\n' "$attempt" | awk -v log_line="$log_line" '
    /Application run failed/ { on = 1; n = 0; in_block = 0; next }
    !on { next }
    $0 ~ log_line { on = 0; next }
    /^[[:space:]]+at / || /^[[:space:]]+\.\.\. [0-9]+ / || /^[[:space:]]*Suppressed:/ { in_block = 0; next }
    /^Caused by: / || n == 0 { n++; blocks[n] = ""; in_block = 1 }
    in_block { blocks[n] = blocks[n] $0 "\n" }
    END { for (i = (n > 3 ? n - 2 : 1); i <= n; i++) printf "%s", blocks[i] }')
fi

if [ -z "$cause" ]; then
  cause=$(printf '%s\n' "$attempt" | tail -n 40)
fi

printf '%s' "$cause" | jq -Rrs 'sub("^\n+"; "") | sub("\n+$"; "") | if length > 2500 then "…(앞부분 생략)\n" + .[-2500:] else . end'
