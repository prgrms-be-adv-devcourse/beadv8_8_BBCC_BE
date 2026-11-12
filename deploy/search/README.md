# 검색 Elasticsearch (t3.medium k3s)

검색 서비스(`apps/search-service`)가 쓰는 Elasticsearch 입니다. 앱 서버(t3.large)의 메모리를 덜려고 모니터링과 같은 t3.medium k3s 에 둡니다.
검색 서비스는 t3.large compose 에 그대로 있고 VPC 안에서 medium 사설 IP 의 NodePort 로 붙습니다.

| 무엇이 | 값 |
| --- | --- |
| namespace | `search` |
| Deployment · Service | `elasticsearch`, Service `elasticsearch:9200`, NodePort `30920` |
| 데이터 | PVC `elasticsearch-data` 10Gi (local-path, 노드 디스크) |
| 이미지 | `docker/elasticsearch`(공식 + nori)를 deploy 워크플로가 GHCR(private)에 올린 것. 태그는 그 폴더의 트리 해시 |
| pull secret | `ghcr-pull`. 배포 잡의 `GITHUB_TOKEN` 으로 배포마다 다시 만든다(서버에 장기 토큰을 두지 않는다, #281) |
| 메모리 | heap 512MB, request 1Gi · limit 1280Mi (heap 1GB 때 heap 사용 약 250MiB, heap 512MB 로컬 실측 anon 872MiB) |
| 검색 서비스 접속 | `ELASTICSEARCH_URIS=http://<medium 사설 IP>:30920`. IP 는 레포(공개)에 쓰지 않고 deploy 워크플로가 Environment 변수 `MONITORING_PRIVATE_IP` 로 서버 `.env` 에 붙인다 |

dev · prod 는 이 ES 하나를 색인 이름 앞의 환경 이름(`SEARCH_INDEX_PREFIX`, `dev-*` · `prod-*`)으로 나눠 씁니다. 배포(dev 는 기능 브랜치 배포 포함)가 `docker/elasticsearch` 가 바뀌어 이 ES 의 이미지를 바꾸면 ES 가 다시 뜨는 동안 두 환경의 검색이 함께 멈춥니다.

## 준비 (처음 한 번)

medium 의 k3s(노드 라벨 `kidly/role=infra`, 없으면 Pod 가 Pending 으로 남아 배포가 300초 뒤 실패) · deploy 유저(`~/.kube/config`) · GitHub 변수(`MONITORING_SSH_HOST` · `MONITORING_SSH_KNOWN_HOSTS` · `MONITORING_PRIVATE_IP`)가 있어야 합니다. 설치 방법은 모니터링 준비(`deploy/monitoring/README.md`, #613)에 있습니다.
검색을 켤 환경(지금은 Environment `dev`)에 세 변수가 있어야 합니다.

1. medium 에서 ES 가 요구하는 커널 값을 올립니다(재부팅 뒤에도 남게 파일로).

   ```bash
   echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elasticsearch.conf
   sudo sysctl --system
   sysctl vm.max_map_count   # 262144
   ```

2. medium 보안 그룹(`team04-monitoring-sg`)에 인바운드 30920 을 소스 보안 그룹 `team04-sg`(t3.large)로 엽니다.

## 배포

`.github/workflows/deploy.yml` 이 앱 배포 때마다 같이 합니다. 배포 환경의 `deploy/.env.<환경>` 의 `COMPOSE_PROFILES` 에 `search` 가 있을 때만 돕니다.

1. `elasticsearch` job: `elasticsearch.yaml` · `deploy.sh` 를 medium 의 `/opt/app/search` 에 올리고 `deploy.sh` 를 부릅니다.
   `deploy.sh` 는 stdin 으로 받은 토큰으로 pull secret 을 만들고, `__ES_IMAGE__` 를 이미지로 바꿔 `kubectl apply --server-side` 한 뒤 Pod 가 준비될 때까지(최대 300초) 기다립니다
2. `deploy` job: 위가 끝난 뒤 t3.large 에 앱 · 검색 서비스를 올립니다. 서버 `.env` 에 `ELASTICSEARCH_URIS` 가 붙습니다

이미지 태그와 `elasticsearch.yaml` 의 Pod 설정(환경 변수 · 리소스 · probe)이 그대로면 Pod 는 다시 뜨지 않습니다. 둘 중 하나가 바뀌면 다음 배포 때 다시 뜹니다(Recreate 라 2분쯤 검색이 끊깁니다).
GHCR 에 그 태그 이미지가 없으면 `elasticsearch` job 이 서버를 건드리기 전에 멈춥니다(돌던 ES 는 그대로).

## 확인

medium 에서 deploy 유저로(`sudo -u deploy -i`):

```bash
kubectl -n search get pods                                    # Running · READY 1/1
curl -s 'localhost:30920/_cluster/health?pretty'              # status yellow 또는 green
curl -s 'localhost:30920/_cat/plugins'                        # analysis-nori
curl -s 'localhost:30920/_cat/indices?v'                      # dev-products · dev-sellers
kubectl -n search logs deploy/elasticsearch --tail 100
```

t3.large 에서 `curl -s http://<medium 사설 IP>:30920/_cluster/health` 가 되면 보안 그룹이 열린 것입니다.

## 색인 다시 채우기

ES 를 새로 만들면(처음 옮길 때, PVC 를 지웠을 때) 색인이 비어 있습니다. 검색 서비스는 이벤트로만 색인하므로 판매자 · 상품 이벤트를 다시 보냅니다(#577).

```bash
# 관리자 토큰으로. 판매중 · 판매중지 상품마다 상품 정보 변경됨을 한 번씩 다시 보내고 보낸 수를 돌려준다
# 판매자를 먼저(상호명), 그다음 상품. prod 는 https://api.kidly.kro.kr
curl -X POST -H "Authorization: Bearer <관리자 토큰>" https://dev-api.kidly.kro.kr/api/v1/admin/sellers/changed-events
curl -X POST -H "Authorization: Bearer <관리자 토큰>" https://dev-api.kidly.kro.kr/api/v1/admin/products/changed-events
```

prod 는 검색 서비스가 처음 뜬 main 배포 뒤에 한 번 채웁니다.

## t3.large 에서 옮긴 뒤 정리

첫 배포에서 앱 배포(`deploy/deploy.sh` 의 `--remove-orphans`)가 t3.large 의 옛 ES 컨테이너(`dev-elasticsearch-1`)를 지웁니다. 볼륨과 이미지는 남으므로 확인이 끝나면 t3.large 에서 지웁니다.

```bash
docker volume rm dev_elasticsearch-data
docker images 'ghcr.io/*/*-elasticsearch'    # 남은 옛 ES 이미지를 보고 docker rmi 로 지운다
```

## ImagePullBackOff 가 나면

노드가 디스크 정리 등으로 이미지를 지운 뒤 Pod 가 다시 뜨면, pull secret 의 토큰(지난 배포 잡의 `GITHUB_TOKEN`)이 이미 만료돼 이미지를 받지 못합니다.
GitHub Actions → deploy → develop 에서 Run workflow 로 배포를 다시 돌리면 새 토큰으로 받습니다(옛 run 의 Re-run 은 그 커밋의 앱을 다시 올리므로 쓰지 않습니다). 그동안 검색은 실패합니다.

```bash
kubectl -n search describe pod -l app=elasticsearch | sed -n '/^Events:/,$p'   # 원인 확인
```

## 장애 대응 (#1026)

검색 서비스는 장애가 나도 다른 서비스를 막지 않습니다. 대신 검색이 503 을 주거나, 색인이 늦거나, 의미 검색 없이 키워드로만 찾습니다.
알림은 Slack `#4팀-운영이슈` 로 옵니다(`deploy/monitoring/grafana/provisioning/alerting/search.yml`). 지표 · 로그 라벨에는 검색어 · 상품 ID 를 넣지 않습니다.

| 알림 · 증상 | 뜻 | 아래 절차 |
| --- | --- | --- |
| 검색 503 (`SEARCH_UNAVAILABLE`), 서비스 5xx 비율 알림 | Elasticsearch 에 붙지 못하거나 3초 안에 답이 없음 | Elasticsearch 내려감 · 느림 |
| 검색 색인 실패 메시지 도착 | 상품 · 판매자 변경을 색인하지 못해 DLT(`kidly.{product,seller}.events.search.dlt`)로 감. 검색이 다시 읽어 저절로 반영 | 대개 Elasticsearch 장애. 반영되는지만 봅니다 |
| 검색 색인 실패 메시지 미반영 (30분) | DLT 를 다 읽지 못함 | DLT 가 줄지 않음 |
| 의미 검색 임베딩 API 차단 (10분) | OpenAI 가 연속 실패해 부르지 않음. 검색은 키워드로만 | OpenAI 키 만료 · 한도 초과 |
| 의미 검색 벡터 없는 상품 (1시간) | 임베딩 API 를 못 쓴 동안 색인된 상품이 의미 검색에 안 나옴 | 벡터 없는 상품 다시 채우기 |

지표: `kidly_search_embedding_circuit_open`(차단 열림 1), `kidly_search_products_without_embedding`(벡터 없는 판매중 상품 수, 의미 검색을 켠 환경만. 셀 수 없으면 NaN). DLT 는 kafka-exporter 의 오프셋 · 구독 그룹 `kidly.search` lag 로 봅니다.
Elasticsearch 응답 시간은 따로 재지 않습니다. 검색 API 지연(`http_server_requests`, `/api/v1/products`)이 거의 Elasticsearch 시간이고, 3초를 넘으면 503 이 되어 5xx 알림으로 잡힙니다.

### Elasticsearch 내려감 · 느림

검색은 연결 1초 · 응답 3초(`search.yml` `spring.elasticsearch.*`)를 넘으면 503 으로 답합니다. 그동안 받은 상품 · 판매자 변경은 1초 간격 3번 더 시도한 뒤 DLT 로 갑니다.

1. medium 에서 상태를 봅니다(위 "확인"): `kubectl -n search get pods`, `_cluster/health`, `kubectl -n search logs deploy/elasticsearch --tail 200`
2. Pod 가 다시 뜨는 중이면(배포 · OOM) 준비될 때까지 기다립니다. `Pending` 이면 노드 라벨 · 메모리, `ImagePullBackOff` 면 위 절차입니다
3. **디스크 부족**: `curl -s 'localhost:30920/_cat/allocation?v'` 로 봅니다. 디스크가 95%(flood stage)를 넘으면 Elasticsearch 가 색인을 읽기 전용으로 막아 색인이 모두 실패(DLT)합니다.
   PVC 는 10Gi 입니다. `_cat/indices?v` 에서 쓰지 않는 색인(옛 접두어 등)을 지워 공간을 만들면, 기준 아래로 내려갈 때 Elasticsearch 가 막음을 저절로 풉니다. 지울 색인은 담당과 정합니다(`dev-*` · `prod-*` 는 지우지 않습니다)
4. 복구 확인: `_cluster/health` yellow · green → 검색 API 200 → DLT 미반영 알림이 없고 구독 그룹 `kidly.search` 의 DLT lag 가 0

### 색인 유실

Elasticsearch 를 새로 만들었거나(PVC 삭제) 색인이 지워진 경우입니다. 검색 서비스는 **기동할 때만** 색인을 만듭니다.

1. `_cat/indices?v` 에서 `<환경>-products` · `<환경>-sellers` 가 있는지 봅니다
2. 없으면 그 환경의 검색 서비스를 다시 시작해 색인을 만듭니다(t3.large 에서 `docker compose -p <환경> restart search-service`)
3. 검색 서비스를 다시 시작하기 전에 이벤트가 와서 색인이 자동으로 만들어졌을 수 있습니다. `curl -s 'localhost:30920/<환경>-products/_mapping'` 에 `"dynamic":"strict"` 와 `name` 의 `"analyzer":"nori"` 가 없으면 잘못 만들어진 것이므로, 그 색인을 지우고 2 를 다시 합니다
4. 위 "색인 다시 채우기"로 판매자 → 상품을 다시 발행합니다

### DLT 가 줄지 않음

검색은 DLT 를 다시 읽어 색인에 반영합니다(`SearchDeadLetterListener`). 실패하면 1초부터 두 배씩 최대 1분 간격으로 될 때까지 다시 시도하고, 그동안 다음 DLT 메시지는 기다립니다(원래 토픽은 따로 받습니다).
같은 변경을 여러 번 반영해도 늦게 일어난 변경만 남으므로 다시 반영해도 안전합니다. 원래 토픽에 다시 넣지 않습니다.

1. Elasticsearch 가 정상인지 먼저 봅니다(위 절차). 대개 Elasticsearch 가 돌아오면 몇 분 안에 줄어듭니다
2. Elasticsearch 가 정상인데 줄지 않으면 검색 서비스 로그(Loki `service_name=kidly-search`)에서 같은 `eventId` 로 되풀이되는 실패의 예외 종류를 봅니다. 다시 해도 안 되는 오류(색인 설정과 맞지 않는 값 등)면 검색 코드 버그이므로 고쳐서 배포합니다
3. 오프셋을 옮기거나 DLT 를 지우지 않습니다(`deploy/monitoring/docs/operations-readiness.md`). 꼭 건너뛰어야 하면 담당이 정하고, 건너뛴 변경은 다시 발행으로 채웁니다
4. 로그의 `DLT 이벤트를 색인에 반영하지 못해 건너뜁니다` 는 다시 해도 안 되는 메시지(읽지 못한 본문 · 옵션 없는 상품 등)를 건너뛴 것입니다. 그 상품 · 판매자는 옛 값이 남으므로 다시 발행으로 채웁니다

검색 서비스가 DLT 를 처음 읽을 때(이 기능을 처음 배포할 때)는 그때부터 들어온 메시지만 읽습니다(`auto-offset-reset: latest`). 그 전에 쌓인 DLT 는 다시 발행으로 채웁니다.

### OpenAI 키 만료 · 한도 초과

임베딩 API 가 연속 5번 실패하면 30초씩 부르지 않고 키워드로만 찾습니다. 30초마다 하나만 시험으로 불러 성공하면 저절로 다시 씁니다.

1. 검색 서비스 로그에서 `임베딩 API` 실패의 상태 코드를 봅니다. 401 은 키 만료 · 폐기, 429 는 한도 초과, 5xx · 시간 초과는 OpenAI 장애입니다
2. 401: OpenAI 에서 새 키를 만들어 GitHub Environment Secret `OPENAI_API_KEY` 를 바꾸고, deploy 워크플로를 다시 돌려 서버 `.env` 에 넣습니다. 429: OpenAI 결제 · 한도를 봅니다. 5xx: 기다립니다
3. 복구 확인: `kidly_search_embedding_circuit_open` 이 0 → 아래 "벡터 없는 상품 다시 채우기"

### 벡터 없는 상품 다시 채우기

임베딩 API 를 쓸 수 없던 동안 색인된 상품은 벡터 없이 저장되어, 다음 변경 전까지 의미 검색에 나오지 않습니다(키워드 검색은 됩니다).
상품 다시 발행(위 "색인 다시 채우기"의 상품 API 만)으로 채웁니다. 문장이 같은 상품은 저장된 벡터를 다시 쓰므로 OpenAI 는 벡터 없는 상품만큼만 부릅니다(대신 상품마다 Elasticsearch 조회 한 번).
임베딩 차단이 열려 있으면 다시 벡터 없이 저장되므로, 차단이 닫힌 뒤에 합니다. 복구 확인: `kidly_search_products_without_embedding` 이 0

### dev 에서 훈련하기

이 Elasticsearch 는 dev · prod 가 함께 씁니다. 멈추면 prod 검색도 멈추므로 prod 를 운영하는 동안에는 팀과 정한 시간에만 합니다.

```bash
# medium 에서 deploy 유저로
kubectl -n search scale deploy/elasticsearch --replicas=0
# → dev 검색 API 가 약 3초 뒤 503, 판매자 화면에서 상품을 하나 고치면 몇 초 뒤 "검색 색인 실패 메시지 도착" 알림
kubectl -n search scale deploy/elasticsearch --replicas=1
kubectl -n search rollout status deploy/elasticsearch --timeout=300s
# → 검색 API 200, 고친 상품이 검색에 반영되고 구독 그룹 kidly.search 의 DLT lag 0
```

## 알려진 한계

- **인증이 없습니다(xpack 보안 끔).** 막는 것은 보안 그룹(소스 `team04-sg`)뿐이라, 이 보안 그룹을 넓히면 누구나 색인을 읽고 지울 수 있습니다.
- **NodePort(30920)는 노드의 모든 주소에 열립니다.** 탄력적 IP 쪽도 보안 그룹으로만 막힙니다.
- **노드가 이미지를 지우면 다음 배포까지 다시 받지 못합니다.** 위 ImagePullBackOff 를 봅니다.
- **dev 앱 배포가 medium 에 묶입니다.** medium 이 꺼져 있으면 `elasticsearch` job 이 SSH 에서 멈춰 앱 배포도 하지 않습니다.
- **검색 서비스는 ES 에 못 붙으면 뜨지 않습니다.** 아침 자동 기동 때 t3.large 가 먼저 뜨면 재시작을 반복하다 ES 가 준비되면 스스로 올라옵니다(`restart: unless-stopped`).
- **노드가 하나입니다.** medium 이 꺼지거나 Pod 가 다시 뜨는 동안 검색 서비스는 검색 · 기동에 실패합니다.
