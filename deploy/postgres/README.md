# PostgreSQL (t3.medium k3s)

서비스들이 쓰는 PostgreSQL 입니다. 앱 서버(t3.large)의 메모리를 덜려고 Elasticsearch · Kafka 브로커와 같은 t3.medium k3s 에 둡니다.
인스턴스는 하나이고 dev · prod 가 database · 계정으로 나눠 씁니다. 서비스는 t3.large compose 에 그대로 있고 VPC 안에서 medium 사설 IP 의 NodePort 로 붙습니다.

| 무엇이 | 값 |
| --- | --- |
| namespace | `postgres` |
| Deployment · Service | `postgres`, NodePort `30432` |
| 데이터 | PVC `postgres-data` 10Gi (local-path, 노드 디스크) |
| 이미지 | `pgvector/pgvector:pg18-trixie` (추천의 벡터 칸), `max_connections=120` |
| 메모리 | request 256Mi · limit 512Mi |
| 환경별 | 계정 · database `kidly_dev` · `kidly_prod`. 슈퍼유저가 아니고 자기 database 만 쓴다(다른 환경 계정은 connect 권한이 없다) |
| 관리자 | `postgres`. 비밀번호는 Secret `postgres-admin`(처음 배포 때 무작위). 부트스트랩은 Pod 안 로컬 소켓으로 접속한다 |
| 서비스 접속 | `DB_URL=jdbc:postgresql://<medium 사설 IP>:30432/kidly_<환경>`, `DB_USERNAME=kidly_<환경>`(`deploy/.env.<환경>`), 비밀번호는 Environment Secret `DB_PASSWORD` |

서비스마다 커넥션 풀은 5개입니다(`spring.datasource.hikari.maximum-pool-size`). 서비스 8개 × 5 × 두 환경 = 80 연결이 `max_connections` 120 안에 듭니다.
pgvector 는 trusted 확장이 아니라 앱 계정이 켜지 못하므로 부트스트랩이 database 마다 `recommend` 스키마에 먼저 켭니다. 확장이 더 필요해지면 `deploy.sh` 의 부트스트랩에 더합니다.

## 준비 (처음 한 번)

medium 의 k3s(노드 라벨 `kidly/role=infra`) · deploy 유저(`~/.kube/config`)는 Elasticsearch 와 같습니다(`deploy/search/README.md`).

1. medium 보안 그룹(`team04-monitoring-sg`)에 인바운드 30432 를 소스 보안 그룹 `team04-sg`(t3.large)로 엽니다.
2. 배포할 Environment 에 변수 `MONITORING_SSH_HOST` · `MONITORING_SSH_KNOWN_HOSTS` · `MONITORING_PRIVATE_IP` 가 있어야 합니다.

## 배포

`.github/workflows/deploy.yml` 이 앱 배포 때마다 같이 합니다.

1. `postgres` job: `postgres.yaml` · `deploy.sh` 를 medium 의 `/opt/app/postgres-<환경>` 에 올리고, `DB_PASSWORD` 를 stdin 으로 넘겨 `deploy.sh` 를 부릅니다. `deploy.sh` 는 인스턴스를 띄운 뒤 그 환경의 계정 · database · 확장을 맞춥니다(몇 번 돌려도 같다)
2. `deploy` job: 위가 성공한 뒤에만 t3.large 에 앱을 올립니다. 서버 `.env` 에 `DB_URL` 이 붙습니다

## compose postgres 에서 옮기기 (환경마다 한 번)

`migrate-from-compose.sh` 가 t3.large 의 `<환경>-postgres-1`(database `kidly`)을 덤프해 `kidly_<환경>` 에 앱 계정으로 복원하고 테이블별 행 수를 비교합니다.
대상 database 에 테이블이 있으면 멈춥니다. 덤프 뒤 원본에 들어온 쓰기는 따라오지 않으므로 앱을 새 DB 로 바꾸는 배포 직전에 돌립니다.

```bash
APP_ENV=dev \
APP_SSH="-i ~/.ssh/<키> ec2-user@<t3.large IP>" \
INFRA_SSH="-i ~/.ssh/<키> ec2-user@<t3.medium IP>" \
deploy/postgres/migrate-from-compose.sh
```

옮긴 뒤에도 옛 볼륨(`<환경>_postgres-data`)은 compose 가 지우지 않습니다. 새 DB 로 잘 도는 것을 확인한 뒤 지웁니다.

## 팀원 접속

medium 으로 SSH 터널을 열고 `localhost:15432`, database · 계정 `kidly_<환경>` 으로 붙습니다.

```bash
ssh -i ~/.ssh/<키> -N -L 15432:<medium 사설 IP>:30432 ec2-user@<t3.medium IP>
```

medium 이 멈추면 두 환경의 DB · Kafka 브로커 · Elasticsearch · 모니터링이 함께 멈춥니다.
