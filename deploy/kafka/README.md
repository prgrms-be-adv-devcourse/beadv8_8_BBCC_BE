# Kafka 브로커 (t3.medium k3s)

서비스들이 도메인 이벤트를 주고받는 Kafka API 호환 브로커(Redpanda)입니다. 앱 서버(t3.large)의 메모리를 덜려고 Elasticsearch(`deploy/search`)와 같은 t3.medium k3s 에 둡니다.
서비스 · kafka-exporter 는 t3.large compose 에 그대로 있고 VPC 안에서 medium 사설 IP 의 NodePort 로 붙습니다.

| 무엇이 | 값 |
| --- | --- |
| namespace | `kafka-dev` · `kafka-prod`. 토픽 이름에 환경 구분이 없어 환경마다 브로커를 따로 둔다 |
| Deployment · Service | `redpanda`, NodePort dev `30092` · prod `30093` (`deploy/.env.<환경>` 의 `KAFKA_NODE_PORT`) |
| 데이터 | PVC `redpanda-data` 5Gi (local-path, 노드 디스크) |
| 이미지 | `redpandadata/redpanda:v26.2.6`, `--mode dev-container --smp 1 --memory 256M` |
| 메모리 | request 256Mi · limit 384Mi (로컬 실측 약 100MiB) |
| 서비스 접속 | `KAFKA_BOOTSTRAP_SERVERS=<medium 사설 IP>:<NodePort>`. 브로커도 이 주소를 알린다(advertised listener). IP 는 레포(공개)에 쓰지 않고 deploy 워크플로가 Environment 변수 `MONITORING_PRIVATE_IP` 로 서버 `.env` 에 붙인다 |

dev-container 모드는 fsync 를 건너뛰고 쓰기 캐시를 켭니다. 실제 운영 전 학습용 환경이라 prod 도 이 모드로 띄웁니다. 토픽 자동 생성은 끄고(`auto_create_topics_enabled=false`), 보존 7일 · 리밸런스 지연 0 은 이 모드의 기본값과 같습니다.

## 준비 (처음 한 번)

medium 의 k3s(노드 라벨 `kidly/role=infra`) · deploy 유저(`~/.kube/config`)는 Elasticsearch 와 같습니다(`deploy/search/README.md`).

1. medium 보안 그룹(`team04-monitoring-sg`)에 인바운드 30092(dev) · 30093(prod)을 소스 보안 그룹 `team04-sg`(t3.large)로 엽니다. 브로커는 인증이 없어 이 규칙이 유일한 접근 제한입니다.
2. 배포할 Environment 에 변수 `MONITORING_SSH_HOST` · `MONITORING_SSH_KNOWN_HOSTS` · `MONITORING_PRIVATE_IP` 가 있어야 합니다. 없으면 kafka job 이 서버를 바꾸기 전에 실패합니다.

## 배포

`.github/workflows/deploy.yml` 이 앱 배포 때마다 같이 합니다.

1. `kafka` job: `redpanda.yaml` · `deploy.sh` 를 medium 의 `/opt/app/kafka-<환경>` 에 올리고 `deploy.sh` 를 부릅니다. `deploy.sh` 는 `__APP_ENV__` · `__NODE_PORT__` · `__ADVERTISED_HOST__` 를 바꿔 `kubectl apply --server-side` 한 뒤 브로커가 준비될 때까지(최대 300초) 기다립니다
2. `deploy` job: 위가 성공한 뒤에만 t3.large 에 앱을 올립니다. 서버 `.env` 에 `KAFKA_BOOTSTRAP_SERVERS` 가 붙습니다

medium 이 멈추면 두 환경의 브로커 · Elasticsearch · 모니터링이 함께 멈춥니다. 서비스는 브로커가 돌아오면 다시 붙습니다.
