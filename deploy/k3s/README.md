# 앱 서비스 k3s (t3.large)

dev 의 서비스를 compose 대신 t3.large 에 단독으로 띄운 k3s(노드 하나)에 올립니다(#1028 추천, #1142 나머지). Gateway · kafka-exporter 는 compose 에 그대로 있습니다. medium 의 k3s(Kafka · PostgreSQL · Elasticsearch · 모니터링)와는 따로 돕니다.

| 무엇이 | 값 |
| --- | --- |
| 어떤 서비스가 | `deploy/.env.<환경>` 의 `K3S_PROFILES` 에 있는 서비스. 지금 dev 의 서비스 9개 모두(1차 #1148 검색 · 추천 · 정산 · 회원 · 결제, 2차 상품 · 주문 · 커뮤니티 · 알림). 한 profile 은 `COMPOSE_PROFILES` · `K3S_PROFILES` 중 한쪽에만 둔다 |
| NodePort | `<PROFILE>_NODE_PORT` 가 있는 서비스만 이 서버의 그 포트로 닿는다(compose 의 Gateway 가 붙는 곳). 없으면 클러스터 안에서만 `<이름>:8080` |
| namespace | `kidly-<환경>`. Deployment · Service 이름은 compose profile 이름(`search`, `member` 등) |
| 설정 | 서버의 `docker-compose.yml` · `.env` 의 그 서비스 설정을 그대로 쓴다. 환경 변수는 Secret `<이름>-env`, jwt 키는 Secret `<이름>-jwt`, 메모리는 `mem_limit` 을 request · limit 으로, 이미지는 `image.env` 의 그 서비스 이미지 |
| Gateway 접속 | `KIDLY_<서비스>_URI=http://host.docker.internal:<NodePort>`. NodePort 는 보안 그룹에서 밖으로 열지 않는다 |
| 배포 | 서비스 하나씩 차례로. 새 Pod 가 준비된 뒤 옛 Pod 를 내린다. 상품 · 알림은 스케줄러가 두 번 돌면 안 돼서 옛 Pod 를 먼저 내린다(`k3s.py` 의 `SINGLE_INSTANCE`) |
| 자동 확장 | 추천만 HPA(CPU 70%, 1~3개). 나머지는 1개 |
| 지표 · 로그 | monitoring-agent Alloy 가 k3s API 로 Pod 를 찾는다. job · `service_name` 은 compose 때와 같은 `kidly-<이름>`, `instance` 는 Pod 이름 |

## 어떻게 도는가

`deploy.yml` 의 deploy job 이 `docker-compose.yml` · `.env` · `jwt` 와 함께 `k3s/k3s.py` 를 `/opt/app/<환경>` 에 올리고 `deploy.sh` 를 부릅니다. `deploy.sh` 는 compose 앱보다 먼저 다음을 합니다.

1. `environment-state.py k3s <환경>` 으로 k3s 서비스와 NodePort 를 읽는다
2. namespace 와 pull secret(`ghcr-pull`, 이번 배포 세션의 GHCR 로그인)을 만든다
3. 서비스마다 `docker compose config --format json <서비스>` 를 `k3s.py manifest` 에 넘겨 Secret · Deployment · Service(· HPA)를 적용하고, 준비될 때까지(최대 420초) 기다린다. 안 되면 로그 · 이벤트를 남기고 멈춘다
4. compose 를 올린다. Gateway 가 NodePort 주소로 다시 뜬다
5. k3s 로 옮겨 간 서비스의 옛 compose 컨테이너를 지운다

## 서비스 옮기기 · 되돌리기

옮기기: `.env.<환경>` 에서 그 profile 을 `COMPOSE_PROFILES` 에서 `K3S_PROFILES` 로 옮기고, `<PROFILE>_PORT` 를 `<PROFILE>_NODE_PORT=<30000~32767>` 로 바꾸고, `KIDLY_<서비스>_URI` 를 `http://host.docker.internal:<NodePort>` 로 바꿉니다.

처음 옮기는 배포에서는 Gateway 가 새 주소로 다시 뜰 때까지 같은 서비스가 compose 와 k3s 에 함께 떠 메모리를 두 배로 씁니다. 서버 여유(`free -m` 의 available)보다 옮길 서비스의 사용량 합이 충분히 작게 나눠 옮깁니다.

되돌리기: 위를 반대로 하고 배포한 뒤 `kubectl -n kidly-<환경> delete deploy,svc,secret,hpa -l app=<이름>`.

## 확인

t3.large 에서 deploy 유저로(`sudo -u deploy -i`):

```bash
kubectl -n kidly-dev get deploy,pods,hpa -o wide
kubectl -n kidly-dev logs deploy/member --tail 100
curl -s localhost:30204/actuator/health     # Gateway 가 붙는 경로
```

## 알려진 한계

- **environment-operation 의 dev 끄기 · 켜기**: compose 를 멈춘 뒤 k3s Deployment 를 0개로, 켤 때는 compose 보다 먼저 1개로 돌린다(추천은 다시 HPA 가 맡는다)
- **노드가 이미지를 지우면 다음 배포까지 다시 받지 못한다.** pull secret 의 토큰이 배포 잡이 끝나면 만료된다. `deploy/search/README.md` 의 ImagePullBackOff 와 같다
- **compose 의 `oom_score_adj` · healthcheck 는 쓰지 않는다.** 대신 startup · readiness · liveness probe(`/actuator/health/*`)를 쓴다
- **추천 HPA 와 스케줄러**: 추천 Pod 가 여럿이면 상품 임베딩 스케줄러도 Pod 마다 돈다(#1028 부터 같은 상태)
- **메모리**: request 합이 노드 할당량(약 7.6Gi)을 넘으면 새 Pod 가 Pending 이 된다. compose 컨테이너 몫은 스케줄러가 모르므로 `free -m` 으로 함께 본다
  - dev 서비스 9개의 request 합은 5,312Mi(검색 1,024 · 추천 448 · 정산 640 · 회원 512 · 결제 512 · 상품 512 · 주문 640 · 커뮤니티 512 · 알림 512)다. 추천이 3개로 늘면 6,208Mi. 여기에 compose 의 Gateway(512Mi) · kafka-exporter(128Mi)와 k3s 자체가 더해진다
  - 2차를 처음 배포할 때는 주문 · 커뮤니티(합 1,152Mi)가 compose 와 k3s 에 잠깐 함께 뜬다(상품 · 알림은 옛 컨테이너를 먼저 내린다). 배포 전에 `free -m` 의 available 이 1.5GiB 이상인지 본다
