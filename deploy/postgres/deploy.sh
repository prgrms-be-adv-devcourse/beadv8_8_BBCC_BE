#!/usr/bin/env bash
# medium(k3s) 서버에서 deploy 유저로 실행한다(~/.kube/config). 워크플로(.github/workflows/deploy.yml 의 postgres job)가 이 폴더(/opt/app/postgres-<환경>)에 올린 뒤 부른다
# APP_ENV(dev|prod)는 환경 변수로, 그 환경 앱 계정의 비밀번호(DB_PASSWORD)는 stdin 으로 받는다
# 인스턴스를 띄운 뒤 그 환경의 계정 · database · 확장을 맞춘다. 몇 번을 돌려도 결과가 같다
set -euo pipefail
: "${APP_ENV:?APP_ENV 가 필요합니다}"
[[ "$APP_ENV" == dev || "$APP_ENV" == prod ]] || { echo "Invalid APP_ENV"; exit 1; }
cd "$(dirname "$0")"
# k3s 의 kubectl 은 KUBECONFIG 가 없으면 root 만 읽는 /etc/rancher/k3s/k3s.yaml 을 읽으려다 실패한다
export KUBECONFIG="$HOME/.kube/config"

k() { kubectl -n postgres "$@"; }

password=$(cat)
[[ -n "$password" ]] || { echo "::error::stdin 으로 DB_PASSWORD 가 와야 합니다"; exit 1; }

# Secret 을 넣을 namespace 가 먼저 있어야 한다
kubectl create namespace postgres --dry-run=client -o yaml | kubectl apply --server-side -f -

# 관리자 비밀번호는 처음 한 번만 만든다. dev · prod 배포가 같은 인스턴스를 쓰므로 이미 있으면 그대로 둔다
if ! k get secret postgres-admin >/dev/null 2>&1; then
    k create secret generic postgres-admin --from-literal=password="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')" \
        || k get secret postgres-admin >/dev/null
fi

# 손으로 바꾼 필드가 있어도 레포 내용으로 되돌린다(--force-conflicts)
kubectl apply --server-side --force-conflicts -f postgres.yaml

if ! k rollout status deploy/postgres --timeout 300s; then
    echo "::error::PostgreSQL 이 300s 안에 준비되지 않았습니다"
    echo "::group::PostgreSQL 로그 (마지막 100줄)"
    k logs deploy/postgres --tail 100 || true
    echo "::endgroup::"
    echo "::group::Pod 이벤트"
    k describe pod -l app=postgres | sed -n '/^Events:/,/^$/p' || true
    echo "::endgroup::"
    exit 1
fi

# 앱 계정은 슈퍼유저가 아니라 자기 database 만 쓴다. 다른 환경 계정은 접속하지 못한다(connect 권한 회수)
# pgvector 는 trusted 확장이 아니라 앱 계정이 켜지 못하므로 관리자가 recommend 스키마에 먼저 켜 둔다(마이그레이션의 create extension if not exists 는 넘어간다)
# 비밀번호는 명령줄에 남기지 않고 stdin 의 psql 변수로 넘긴다. Pod 안 로컬 소켓으로 관리자 접속한다
{
    printf '\\set name kidly_%s\n' "$APP_ENV"
    printf '\\set pw %s\n' "'$password'"
    cat <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'name') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'name') \gexec
ALTER ROLE :"name" WITH LOGIN NOSUPERUSER PASSWORD :'pw';
SELECT format('CREATE DATABASE %I OWNER %I', :'name', :'name') WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'name') \gexec
REVOKE CONNECT, TEMPORARY ON DATABASE :"name" FROM PUBLIC;
\c :name
CREATE SCHEMA IF NOT EXISTS recommend AUTHORIZATION :"name";
CREATE EXTENSION IF NOT EXISTS vector SCHEMA recommend;
SQL
} | k exec -i deploy/postgres -- psql -v ON_ERROR_STOP=1 -q -U postgres -d postgres
echo "PostgreSQL: kidly_$APP_ENV 계정 · database 준비"
