# 앱 서비스 k3s (t3.large)

dev(운영 서버로 쓴다)의 앱을 compose 대신 t3.large 에 단독으로 띄운 k3s(노드 하나)에 올립니다(#1028 추천, #1142 나머지). 서비스 9개 · Gateway · kafka-exporter 모두 k3s 에 뜨고 dev 는 compose 로 띄우는 것이 없습니다. medium 의 k3s(Kafka · PostgreSQL · Elasticsearch · 모니터링)와는 따로 돕니다.

| 무엇이 | 값 |
| --- | --- |
| 무엇이 뜨나 | `deploy/.env.<환경>` 의 `K3S_PROFILES` 에 있는 profile. 지금 dev 는 서비스 9개(1차 #1148, 2차 #1159) · `gateway` · `kafka-monitoring`(kafka-exporter) · `redis`(캐시, #690). 한 profile 은 `COMPOSE_PROFILES` · `K3S_PROFILES` 중 한쪽에만 둔다 |
| NodePort | `<PROFILE>_NODE_PORT` 가 있는 것만 이 서버의 그 포트로 닿는다. 지금은 Gateway 30200(nginx 가 붙는 곳) 하나. 나머지는 ClusterIP 라 클러스터 안에서만 `<이름>:<포트>` |
| namespace | `kidly-<환경>`. Deployment · Service 이름은 compose profile 이름(`search`, `member` 등) |
| 설정 | 서버의 `docker-compose.yml` · `.env` 의 그 서비스 설정을 그대로 쓴다. 환경 변수는 Secret `<이름>-env`, jwt 키는 Secret `<이름>-jwt`, 메모리는 `mem_limit` 을 request · limit 으로, 이미지는 `image.env` 의 그 서비스 이미지 |
| Gateway → 서비스 | `KIDLY_<서비스>_URI=http://<이름>:8080`(같은 namespace 의 Service). NodePort 는 보안 그룹에서 밖으로 열지 않는다(서비스는 `X-Member-*` 헤더를 그대로 믿는다) |
| nginx → Gateway | `127.0.0.1:30200`(`deploy/nginx`). nginx 도 이 k3s 의 Pod(namespace `edge`, 호스트 네트워크, #1309)이고 앱 배포로 바뀌지 않는다 |
| 배포 | 하나씩 차례로. 새 Pod 가 준비된 뒤 옛 Pod 를 내린다. 순서는 롤링 서비스 → 상품 · 알림(스케줄러가 두 번 돌면 안 돼서 옛 Pod 를 먼저 내림, `SINGLE_INSTANCE`) → Gateway(다른 서비스가 다 뜬 뒤, `EDGE`). 소스가 바뀌지 않았거나(#1169, `.github/scripts/image-key.sh`) 소스는 바뀌어도 실행 jar 가 쓰는 코드가 같은(#1192, `.github/scripts/runtime-key.sh`) 서비스는 이미지 digest 가 같아 다시 뜨지 않는다 |
| 자동 확장 | 추천만 HPA(CPU 70%가 5분 이어질 때, 1 ~ 2개). 나머지는 1개 |
| 하루 한 번 실행(CronJob) | 정산은 매일 한국 시간 10시에 같은 이미지로 일회 실행한다. 영업일과 중복 실행은 앱이 판단한다 |
| Redis | 캐시용(#690). 같은 namespace 에서 `redis:6379`, 비밀번호 없음(ClusterIP 라 밖에서 닿지 않는다). 영속성 없음(Pod 가 다시 뜨면 비어 있다), `maxmemory 64mb` · `allkeys-lru`, 메모리 128Mi. 지표는 긁지 않고 로그만 모은다 |
| 지표 · 로그 | monitoring-agent Alloy(이 k3s 의 namespace monitoring, #1210)가 k3s API 로 Pod 를 찾는다. Pod 라벨 `kidly/job` 을 job · `service_name` 으로 쓴다(compose 때와 같은 `kidly-<이름>`, exporter 는 `kidly-kafka`). `instance` 는 Pod 이름 |
| Spring 이 아닌 것 | kafka-exporter 처럼 `/actuator` 가 없으면 포트 연결로 확인하고, compose 의 `command` · 포트 · `user` · `read_only` · `cap_drop` 을 그대로 옮긴다 |

## 어떻게 도는가

`deploy.yml` 의 deploy job 이 `docker-compose.yml` · `.env` · `jwt` 와 함께 `k3s/k3s.py` 를 `/opt/app/<환경>` 에 올리고 `deploy.sh` 를 부릅니다. `deploy.sh` 는 compose 앱보다 먼저 다음을 합니다.

1. `environment-state.py k3s <환경>` 으로 k3s 에 올릴 것과 NodePort · job 을 읽는다
2. namespace 와 pull secret(`ghcr-pull`, 이번 배포 세션의 GHCR 로그인)을 만든다
3. 서비스마다 `docker compose config --format json <서비스>` 를 `k3s.py manifest` 에 넘겨 Secret · Deployment · Service(· HPA)를 적용하고, 준비될 때까지(최대 420초) 기다린다. 안 되면 로그 · 이벤트를 남기고 멈춘다
4. compose 를 올린다. `COMPOSE_PROFILES` 가 비면 건너뛴다
5. k3s 로 옮겨 간 것의 옛 compose 컨테이너를 지운다
6. k3s 에 쌓인 배포 이미지를 저장소마다 최신 3개만 남기고 지운다(`k3s/images.py`, #1267). k3s 가 쓰는 이미지와 이번 배포 이미지는 남긴다. 아래 「이미지 정리 권한」이 없으면 경고만 남기고 넘어간다

## 서비스 옮기기 · 되돌리기

옮기기: `.env.<환경>` 에서 그 profile 을 `COMPOSE_PROFILES` 에서 `K3S_PROFILES` 로 옮기고, `<PROFILE>_PORT` 줄을 지우고, `KIDLY_<서비스>_URI` 를 `http://<이름>:8080` 으로 바꿉니다. Gateway 가 compose 에 있는 환경이면 Gateway 가 붙도록 `<PROFILE>_NODE_PORT` 를 두고 `http://host.docker.internal:<NodePort>` 로 바꿉니다.

처음 옮기는 배포에서는 Gateway 가 새 주소로 다시 뜰 때까지 같은 서비스가 compose 와 k3s 에 함께 떠 메모리를 두 배로 씁니다. 서버 여유(`free -m` 의 available)보다 옮길 서비스의 사용량 합이 충분히 작게 나눠 옮깁니다.

되돌리기: 위를 반대로 하고 배포한 뒤 `kubectl -n kidly-<환경> delete deploy,svc,secret,hpa,cronjob -l app=<이름>`.

## 이미지 정리 권한

k3s 이미지는 containerd 에 있어 `docker rmi` 로 지울 수 없고, deploy 유저는 sudo 와 containerd 소켓 권한이 없습니다. 그래서 `deploy-crictl.sudoers` 로 crictl 의 `images -q` · `inspecti -o json` · `rmi sha256:<이미지 ID>` 만 비밀번호 없이 허용합니다(#1267). `rmi --prune` · `--all` 처럼 이미지를 한꺼번에 지우는 인자는 막습니다. k3s 의 기본 정리(kubelet image GC)는 디스크 85% 에서 시작해 80% 에서 멈춰, 서버 디스크 알림 기준(80%)보다 늦습니다.

서버를 새로 만들면 한 번 넣습니다. ec2-user 로, 배포가 한 번 돌아 `/opt/app/dev/k3s` 가 있는 상태에서:

```bash
sudo visudo -cf /opt/app/dev/k3s/deploy-crictl.sudoers    # 문법 확인. 틀린 파일이 들어가면 서버의 sudo 전체가 막힌다
sudo install -m 440 -o root -g root /opt/app/dev/k3s/deploy-crictl.sudoers /etc/sudoers.d/kidly-deploy-crictl   # 이름에 . 이 있으면 sudo 가 읽지 않는다
sudo -u deploy sudo -n /usr/local/bin/k3s crictl images -q | wc -l   # 비밀번호를 묻지 않고 숫자가 나오면 된다
```

## 확인

t3.large 에서 deploy 유저로(`sudo -u deploy -i`):

```bash
kubectl -n kidly-dev get deploy,pods,hpa -o wide
kubectl -n kidly-dev get cronjob,job
kubectl -n kidly-dev logs deploy/member --tail 100
curl -s localhost:30200/actuator/health     # nginx 가 붙는 Gateway
```

## 알려진 한계

- **environment-operation 의 dev 끄기 · 켜기**: k3s CronJob 을 suspend · 재개하고 Deployment 를 0개 · 1개로 돌린다(추천은 다시 HPA 가 맡는다)
- **Gateway 를 다시 띄울 때**: 옛 Pod 와 새 Pod 가 겹치는 몇 초 동안 구독 그룹 `kidly.gateway` 의 파티션을 나눠 가져, 새 Pod 가 토큰 무효화 목록을 일부만 안다. 옛 Pod 가 내려가면 모든 파티션을 31분 전부터 다시 읽는다(apps/gateway CLAUDE.md 의 무효화 목록)
- **노드가 이미지를 지우면 다음 배포까지 다시 받지 못한다.** pull secret 의 토큰이 배포 잡이 끝나면 만료된다. `deploy/search/README.md` 의 ImagePullBackOff 와 같다. 배포 끝의 이미지 정리는 k3s 가 쓰는 이미지를 남기므로 떠 있는 Pod 에는 닿지 않고, 지운 옛 이미지로 롤백할 때만 다음 배포가 다시 받는다
- **compose 의 `oom_score_adj` · healthcheck 는 쓰지 않는다.** 대신 startup · readiness · liveness probe(`/actuator/health/*`)를 쓴다
- **추천 HPA 와 스케줄러**: 추천 Pod 가 여럿이면 상품 임베딩 스케줄러도 Pod 마다 돈다(#1028 부터 같은 상태)
- **메모리**: request 합이 노드 할당량(약 7.6Gi)을 넘으면 새 Pod 가 Pending 이 된다. compose 컨테이너 몫은 스케줄러가 모르므로 `free -m` 으로 함께 본다
  - request 합은 6,368Mi(dev 서비스 9개 5,440 · Gateway 512 · kafka-exporter 128 · Redis 128, 수집기 Alloy 128(namespace monitoring, #1210), nginx 32(namespace edge, #1309))다. 추천이 2개로 늘면 6,944Mi. 정산 Job Pod 가 10시에 640Mi, 인증서 갱신 Job 이 10:30에 64Mi를 잠깐 더 요청해 둘이 겹치면 7,648Mi다. 여기에 k3s 자체가 더해진다
  - 추천은 Pod 하나가 576Mi 라 3개를 둘 자리가 없어 HPA 최대를 2개로 둔다(#1237)
