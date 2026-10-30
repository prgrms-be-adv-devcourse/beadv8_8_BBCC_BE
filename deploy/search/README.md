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
| 메모리 | heap 1GB, request 1536Mi · limit 2Gi (실측 최대 1.42GB) |
| 검색 서비스 접속 | `ELASTICSEARCH_URIS=http://<medium 사설 IP>:30920`. IP 는 레포(공개)에 쓰지 않고 deploy 워크플로가 Environment 변수 `MONITORING_PRIVATE_IP` 로 서버 `.env` 에 붙인다 |

색인 이름 앞의 환경 이름(`SEARCH_INDEX_PREFIX`)으로 dev · prod 가 이 ES 하나를 나눠 쓸 수는 있지만, 지금은 dev 만 씁니다. dev 배포(기능 브랜치 배포 포함)가 이 ES 의 이미지를 바꾸고 다시 띄우므로, prod 검색을 켜기 전에 나눌지 다시 정합니다.

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

ES 를 새로 만들면(처음 옮길 때, PVC 를 지웠을 때) 색인이 비어 있습니다. 검색 서비스는 이벤트로만 색인하므로 상품 이벤트를 다시 보냅니다(#577).

```bash
# 관리자 토큰으로. 판매중 · 판매중지 상품마다 상품 정보 변경됨을 한 번씩 다시 보내고 보낸 수를 돌려준다
curl -X POST -H "Authorization: Bearer <관리자 토큰>" https://dev.kidly.kro.kr/api/v1/admin/products/changed-events
```

판매자 색인(`<prefix>-sellers`)을 다시 보내는 API 는 아직 없습니다.

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

## 알려진 한계

- **인증이 없습니다(xpack 보안 끔).** 막는 것은 보안 그룹(소스 `team04-sg`)뿐이라, 이 보안 그룹을 넓히면 누구나 색인을 읽고 지울 수 있습니다.
- **NodePort(30920)는 노드의 모든 주소에 열립니다.** 탄력적 IP 쪽도 보안 그룹으로만 막힙니다.
- **노드가 이미지를 지우면 다음 배포까지 다시 받지 못합니다.** 위 ImagePullBackOff 를 봅니다.
- **dev 앱 배포가 medium 에 묶입니다.** medium 이 꺼져 있으면 `elasticsearch` job 이 SSH 에서 멈춰 앱 배포도 하지 않습니다.
- **검색 서비스는 ES 에 못 붙으면 뜨지 않습니다.** 아침 자동 기동 때 t3.large 가 먼저 뜨면 재시작을 반복하다 ES 가 준비되면 스스로 올라옵니다(`restart: unless-stopped`).
- **노드가 하나입니다.** medium 이 꺼지거나 Pod 가 다시 뜨는 동안 검색 서비스는 검색 · 기동에 실패합니다.
