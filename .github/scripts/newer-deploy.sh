#!/usr/bin/env bash
# 같은 브랜치에서 이 실행보다 새 push 배포가 몇 건인지 출력한다(#1169). 취소된 것은 세지 않는다
# 대기 중인 것뿐 아니라 끝난 것도 센다. 실패한 옛 배포를 다시 돌릴 때 이미 끝난 더 새 배포를 옛 코드로 덮지 않게 한다
# 조회가 실패하면 0 을 낸다(건너뛰지 않고 배포한다. 대기만 길어질 뿐 배포가 빠지지는 않게)
set -uo pipefail
: "${GITHUB_REPOSITORY:?}" "${GITHUB_REF_NAME:?}" "${GITHUB_RUN_NUMBER:?}"
count=$(gh api "repos/$GITHUB_REPOSITORY/actions/workflows/deploy.yml/runs?branch=$GITHUB_REF_NAME&event=push&per_page=30" \
  --jq "[.workflow_runs[] | select(.run_number > $GITHUB_RUN_NUMBER and .conclusion != \"cancelled\")] | length" 2>/dev/null) || count=0
[[ "$count" =~ ^[0-9]+$ ]] || count=0
echo "$count"
