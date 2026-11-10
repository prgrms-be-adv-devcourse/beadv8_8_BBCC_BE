# 추천 서비스 (k3s, t3.large)

추천 서비스(`apps/recommend-service`)를 compose 대신 k3s 에 띄우고 CPU 사용률로 Pod 수를 늘리고 줄입니다(#1028).
t3.large 에 k3s 를 단독으로(노드 하나) 띄웁니다. medium 의 k3s 와는 따로 돌고, 다른 앱 서비스는 t3.large compose 에 그대로 있습니다.
지금은 **dev 만** k3s 에 있고 prod 는 compose 에 있습니다.

| 무엇이 | 값 |
| --- | --- |
| 클러스터 | t3.large 의 단독 k3s. 배포 · kubectl 은 t3.large 에서 한다 |
| namespace | `recommend-<환경>` (지금 `recommend-dev`) |
| Deployment · Service | `recommend`, NodePort dev `30187` (`deploy/.env.<환경>` 의 `RECOMMEND_NODE_PORT`) |
| HPA | CPU request 200m 의 70% 기준, 최소 1 · 최대 3. 구독 그룹이 받는 토픽이 파티션 3 이라 4번째 Pod 부터는 이벤트를 받지 않는다 |
| 메모리 | Pod 마다 request 384Mi · limit 448Mi, 힙 128m (compose 때와 같다) |
| 비밀 | Secret `recommend-secrets`(DB_PASSWORD · OPENAI_API_KEY), pull secret `ghcr-pull`. 배포 잡이 배포마다 만든다(#281) |
| Gateway 접속 | `KIDLY_RECOMMEND_URI=http://host.docker.internal:30187`. Gateway 컨테이너가 같은 서버의 NodePort 로 붙는다 |
| Kafka · DB | compose 서비스와 같은 medium 사설 IP 의 NodePort(30092 · 30432). 보안 그룹은 이미 열려 있다 |
| 지표 · 로그 | t3.large 의 monitoring-agent Alloy(`deploy/monitoring-agent/config.alloy`)가 이 k3s API 로 Pod 를 찾아 모은다. job `kidly-recommend`, `env`, `service_name` 은 compose 때와 같고 `instance` 는 Pod 이름이다 |

## 준비 (처음 한 번, t3.large 에서)

보안 그룹은 바꾸지 않습니다. `team04-sg` 에 30000~32767 을 밖으로 열지 않습니다. NodePort 는 서버의 모든 주소에 열려, 밖에서 닿으면 `X-Member-*` 헤더를 위조할 수 있습니다.

### 1. k3s 설치

medium 과 같은 버전을 씁니다(`deploy/monitoring/docs/setup.md`). 쓰지 않는 기본 구성 요소는 끕니다. traefik · servicelb 는 80 · 443 을 잡아 nginx 와 부딪히고, local-storage 는 쓰는 볼륨이 없습니다.

```bash
free -m   # 설치 전 여유 메모리를 적어 둔다

curl -sfL https://get.k3s.io | sudo INSTALL_K3S_VERSION=v1.37.1+k3s1 sh -s - server \
  --disable traefik --disable servicelb --disable local-storage
```

k3s 는 Docker 와 따로 내장 containerd 를 씁니다. compose 컨테이너는 그대로 돕니다.

### 2. deploy 유저 kubeconfig

배포 잡은 deploy 유저로 들어와 `~/.kube/config` 로 kubectl 을 씁니다.

```bash
sudo mkdir -p /home/deploy/.kube
sudo cp /etc/rancher/k3s/k3s.yaml /home/deploy/.kube/config
sudo chown -R deploy:deploy /home/deploy/.kube
sudo chmod 600 /home/deploy/.kube/config
```

### 3. 확인

```bash
sudo -u deploy kubectl get nodes        # Ready
sudo -u deploy kubectl top nodes        # 1~2분 뒤 값이 나오면 metrics-server 가 돈다(HPA 기준)
free -m                                 # 설치 전보다 줄어든 만큼이 k3s 몫
```

Docker 와 k3s 가 함께 바꾼 iptables 로 기존 서비스가 막히지 않았는지 봅니다.

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://dev-api.kidly.kro.kr/api/v1/products   # 200
```

### 4. PR 머지

위가 끝난 뒤 이 변경을 develop 에 머지합니다. k3s 가 없을 때 머지하면 다음 두 가지가 멈춥니다. 둘 다 서버를 바꾸기 전에 멈추므로 돌던 것은 그대로입니다.

- `recommend` job 이 멈추고 dev 앱 배포도 하지 않는다
- 모니터링 배포(`deploy/monitoring-agent/deploy.sh`)가 멈춘다. Alloy 는 `/etc/rancher/k3s/k3s.yaml` 이 없으면 뜨지 않는다

## 배포

`.github/workflows/deploy.yml` 이 앱 배포 때마다 같이 합니다. `deploy/.env.<환경>` 에 `RECOMMEND_NODE_PORT` 가 있는 환경만 돕니다.

1. `recommend` job(kafka · postgres 뒤): `recommend.yaml` · `deploy.sh` 를 t3.large 의 `/opt/app/recommend-<환경>` 에 올리고 `deploy.sh` 를 부릅니다. `deploy.sh` 는 k3s API 를 확인하고, pull secret · 비밀을 만들고, 매니페스트를 적용한 뒤 새 Pod 가 준비될 때까지(최대 420초) 기다립니다
2. `deploy` job: 위가 성공한 뒤에만 compose 앱을 올리고, 남은 compose 추천 컨테이너를 지웁니다(첫 배포 때 한 번)

롤링 배포라 새 Pod 가 준비된 뒤 옛 Pod 가 내려갑니다. 그동안 Pod 가 하나 더 뜹니다.

## 확인

t3.large 에서 deploy 유저로(`sudo -u deploy -i`):

```bash
kubectl -n recommend-dev get deploy,pods,hpa -o wide
kubectl -n recommend-dev logs deploy/recommend --tail 100
curl -s localhost:30187/actuator/health     # Gateway 가 붙는 경로
```

Grafana 의 서비스 오류 · 로그 대시보드에서 `kidly-recommend` (dev) 가 그대로 보이면 지표 · 로그가 이어진 것입니다.

스케일링은 dev 추천 API 에 부하를 주고 `kubectl -n recommend-dev get hpa -w` 로 Pod 수가 늘었다가, 부하를 빼고 5분 뒤 하나씩 줄어드는지 봅니다. 결과는 #1028 에 남깁니다.

## 되돌리기

1. `deploy/.env.dev` 의 `COMPOSE_PROFILES` 에 `recommend` 를 다시 넣고 `RECOMMEND_NODE_PORT` 줄을 `RECOMMEND_PORT=8087` 로 되돌린 뒤 `KIDLY_RECOMMEND_URI` 줄을 지워 배포합니다
2. `kubectl delete namespace recommend-dev`
3. k3s 를 지울 때(`sudo /usr/local/bin/k3s-uninstall.sh`)는 `deploy/monitoring-agent` 의 k3s 수집도 함께 빼야 합니다. 빼지 않으면 Alloy 가 다시 뜰 때 실패합니다

## 알려진 한계

- **environment-operation 의 dev 끄기는 추천 Pod 를 끄지 않습니다.** compose 만 멈추고 추천 알림만 멈춥니다. 끄려면 `kubectl -n recommend-dev scale deploy/recommend --replicas 0` (HPA 가 있어 켤 때는 `--replicas 1`)
- **노드가 이미지를 지우면 다음 배포까지 다시 받지 못합니다.** pull secret 의 토큰이 배포 잡이 끝나면 만료됩니다. `deploy/search/README.md` 의 ImagePullBackOff 와 같습니다
- **비밀을 바꾸면 다음 이미지 배포 때 들어갑니다.** 바로 넣으려면 `kubectl -n recommend-dev rollout restart deploy/recommend`
- **t3.large 메모리를 k3s 가 함께 씁니다.** 설치 전후 `free -m` 차이와 Pod 최대 3개(448Mi 씩)가 compose 서비스와 함께 들어가는지 봅니다
