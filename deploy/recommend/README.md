# 추천 서비스 (k3s, t3.large 노드)

추천 서비스(`apps/recommend-service`)를 compose 대신 k3s 에 띄우고 CPU 사용률로 Pod 수를 늘리고 줄입니다(#1028).
t3.large 를 medium 클러스터의 agent 노드로 붙여 그 노드에 올립니다. 다른 앱 서비스는 t3.large compose 에 그대로 있습니다.
지금은 **dev 만** k3s 에 있고 prod 는 compose 에 있습니다.

| 무엇이 | 값 |
| --- | --- |
| namespace | `recommend-<환경>` (지금 `recommend-dev`) |
| 노드 | 라벨 `kidly/role=app` (t3.large agent). 없으면 배포가 서버를 바꾸기 전에 멈춘다 |
| Deployment · Service | `recommend`, NodePort dev `30187` (`deploy/.env.<환경>` 의 `RECOMMEND_NODE_PORT`) |
| HPA | CPU request 200m 의 70% 기준, 최소 1 · 최대 3. 구독 그룹이 받는 토픽이 파티션 3 이라 4번째 Pod 부터는 이벤트를 받지 않는다 |
| 메모리 | Pod 마다 request 384Mi · limit 448Mi, 힙 128m (compose 때와 같다) |
| 비밀 | Secret `recommend-secrets`(DB_PASSWORD · OPENAI_API_KEY), pull secret `ghcr-pull`. 배포 잡이 배포마다 만든다(#281) |
| Gateway 접속 | `KIDLY_RECOMMEND_URI=http://host.docker.internal:30187`. Gateway 컨테이너가 같은 서버의 NodePort 로 붙는다 |
| Kafka · DB | compose 서비스와 같은 medium 사설 IP 의 NodePort(30092 · 30432) |
| 지표 · 로그 | medium Alloy(`deploy/monitoring/alloy/config.alloy`)가 Pod 에서 바로 모은다. job `kidly-recommend`, `env`, `service_name` 은 compose 때와 같고 `instance` 는 Pod 이름이다 |

## 준비 (처음 한 번)

### 1. 보안 그룹

| 보안 그룹 | 인바운드 | 소스 | 쓰임 |
| --- | --- | --- | --- |
| `team04-monitoring-sg` (medium) | 6443/tcp | `team04-sg` | agent → API 서버 |
| `team04-monitoring-sg` (medium) | 8472/udp | `team04-sg` | flannel VXLAN (Pod 네트워크) |
| `team04-sg` (large) | 8472/udp | `team04-monitoring-sg` | flannel VXLAN |
| `team04-sg` (large) | 10250/tcp | `team04-monitoring-sg` | 로그 · metrics-server → kubelet |

`team04-sg` 에 30000~32767 을 밖으로 열지 않습니다. NodePort 는 노드의 모든 주소에 열려, 밖에서 닿으면 `X-Member-*` 헤더를 위조할 수 있습니다.

### 2. t3.large 를 agent 로 붙이기

medium 과 같은 k3s 버전을 씁니다(`deploy/monitoring/docs/setup.md`).

```bash
# medium 에서: 합류 토큰
sudo cat /var/lib/rancher/k3s/server/node-token

# t3.large 에서 (ec2-user)
curl -sfL https://get.k3s.io | sudo INSTALL_K3S_VERSION=v1.37.1+k3s1 \
  K3S_URL=https://<medium 사설 IP>:6443 K3S_TOKEN=<토큰> sh -s - agent --node-label kidly/role=app
```

agent 는 Docker 와 따로 내장 containerd 를 씁니다. compose 컨테이너는 그대로 돕니다.

### 3. 합류 확인

medium 에서 deploy 유저로(`sudo -u deploy -i`):

```bash
kubectl get nodes -L kidly/role         # large 가 Ready, 라벨 app
kubectl top nodes                       # 두 노드 값이 나오면 metrics-server 가 돈다(HPA 기준)
kubectl get pods -A -o wide             # 기존 Pod 는 그대로 medium(infra)에 있다
```

t3.large 에서 Docker 와 k3s 가 함께 바꾼 iptables 로 기존 서비스가 막히지 않았는지 봅니다.

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://dev-api.kidly.kro.kr/api/v1/products   # 200
```

### 4. PR 머지

위가 끝난 뒤 이 변경을 develop 에 머지합니다. 노드가 없을 때 머지하면 `recommend` job 이 멈추고 dev 앱 배포도 하지 않습니다.

## 배포

`.github/workflows/deploy.yml` 이 앱 배포 때마다 같이 합니다. `deploy/.env.<환경>` 에 `RECOMMEND_NODE_PORT` 가 있는 환경만 돕니다.

1. `recommend` job(kafka · postgres 뒤): `recommend.yaml` · `deploy.sh` 를 medium 의 `/opt/app/recommend-<환경>` 에 올리고 `deploy.sh` 를 부릅니다. `deploy.sh` 는 앱 노드를 확인하고, pull secret · 비밀을 만들고, 매니페스트를 적용한 뒤 새 Pod 가 준비될 때까지(최대 420초) 기다립니다
2. `deploy` job: 위가 성공한 뒤에만 t3.large 에 compose 앱을 올리고, 남은 compose 추천 컨테이너를 지웁니다(첫 배포 때 한 번)

롤링 배포라 새 Pod 가 준비된 뒤 옛 Pod 가 내려갑니다. 그동안 Pod 가 하나 더 뜹니다.

## 확인

```bash
kubectl -n recommend-dev get deploy,pods,hpa -o wide
kubectl -n recommend-dev logs deploy/recommend --tail 100
# t3.large 에서: Gateway 가 붙는 경로
curl -s localhost:30187/actuator/health
```

Grafana 의 서비스 오류 · 로그 대시보드에서 `kidly-recommend` (dev) 가 그대로 보이면 지표 · 로그가 이어진 것입니다.

스케일링은 dev 추천 API 에 부하를 주고 `kubectl -n recommend-dev get hpa -w` 로 Pod 수가 늘었다가, 부하를 빼고 5분 뒤 하나씩 줄어드는지 봅니다. 결과는 #1028 에 남깁니다.

## 알려진 한계

- **environment-operation 의 dev 끄기는 추천 Pod 를 끄지 않습니다.** compose 만 멈추고 추천 알림만 멈춥니다. 끄려면 `kubectl -n recommend-dev scale deploy/recommend --replicas 0` (HPA 가 있어 켤 때는 `--replicas 1`)
- **노드가 이미지를 지우면 다음 배포까지 다시 받지 못합니다.** pull secret 의 토큰이 배포 잡이 끝나면 만료됩니다. `deploy/search/README.md` 의 ImagePullBackOff 와 같습니다
- **비밀을 바꾸면 다음 이미지 배포 때 들어갑니다.** 바로 넣으려면 `kubectl -n recommend-dev rollout restart deploy/recommend`
- **medium 이 멈추면 API 서버도 멈춥니다.** 돌던 Pod 는 계속 돌지만 다시 뜨거나 늘지 못합니다
