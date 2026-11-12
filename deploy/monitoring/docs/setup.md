# 모니터링 설치·배포

[운영 안내로 돌아가기](../README.md)

## 준비 (처음 한 번)

### 1. EC2 t3.medium

| 항목 | 값 |
| --- | --- |
| VPC · 서브넷 | `team04-vpc` · `team04-subnet-public1-ap-northeast-2a` (t3.large 와 같은 곳. 기본 VPC 아님) |
| 퍼블릭 IP | 자동 할당 끄고 탄력적 IP 연결 (9~18시 자동 정지 · 시작에도 주소가 그대로여야 워크플로가 접속한다) |
| 키 페어 | `team04-pem-key` |
| AMI · 디스크 | Amazon Linux 2023, gp3 30GB 이상 |
| 태그 | `Team=devcos-team04`, `Name=team04-monitoring` |
| 보안 그룹 `team04-monitoring-sg` | 22: 0.0.0.0/0 (GitHub Actions) · 80, 30090, 31100: 소스 보안 그룹 `team04-sg` |

### 2. deploy 유저 · k3s

`ec2-user` 로 접속해 실행합니다. deploy 유저에는 t3.large 의 deploy 유저와 **같은 공개키**를 넣습니다(워크플로가 같은 `SSH_PRIVATE_KEY` 를 씁니다).

```bash
sudo useradd -m deploy
sudo -u deploy mkdir -m 700 /home/deploy/.ssh
echo "<team04-deploy.pub 한 줄>" | sudo -u deploy tee /home/deploy/.ssh/authorized_keys > /dev/null
sudo chmod 600 /home/deploy/.ssh/authorized_keys

# control plane 을 이 서버에 둔다. 나중에 t3.large 는 agent 로 합류한다
curl -sfL https://get.k3s.io | sudo INSTALL_K3S_VERSION=v1.37.1+k3s1 sh -s - server --node-label kidly/role=infra

# deploy 유저가 kubectl 을 쓰게 한다(클러스터 관리자 권한)
sudo mkdir -p /home/deploy/.kube /opt/app/monitoring
sudo cp /etc/rancher/k3s/k3s.yaml /home/deploy/.kube/config
sudo chown -R deploy:deploy /home/deploy/.kube /opt/app
sudo chmod 600 /home/deploy/.kube/config
# k3s 의 kubectl 은 KUBECONFIG 가 없으면 root 만 읽는 /etc/rancher/k3s/k3s.yaml 을 읽는다. deploy.sh 는 스스로 넣는다
echo 'export KUBECONFIG=$HOME/.kube/config' | sudo -u deploy tee -a /home/deploy/.bashrc > /dev/null
sudo -u deploy KUBECONFIG=/home/deploy/.kube/config kubectl get nodes   # Ready 가 보이면 된다
```

### 3. GitHub 변수 · Secret

Environment `dev` 에 등록합니다.

```bash
gh variable set MONITORING_SSH_HOST --env dev --body "<t3.medium 탄력적 IP>"
gh variable set MONITORING_SSH_KNOWN_HOSTS --env dev --body "$(ssh-keyscan -H <t3.medium 탄력적 IP> 2>/dev/null)"
gh variable set MONITORING_PRIVATE_IP --env dev --body "<t3.medium 사설 IP>"
```

Secret 두 개는 이미 있습니다(`SLACK_OPS_WEBHOOK_URL`, `GRAFANA_ADMIN_PASSWORD`). 새로 만들 때는 값을 물어보면 붙여 넣습니다(명령줄 · 화면에 남지 않습니다).

```bash
gh secret set SLACK_OPS_WEBHOOK_URL --env dev
gh secret set GRAFANA_ADMIN_PASSWORD --env dev
```

값에 작은따옴표 · 줄바꿈은 쓸 수 없습니다(워크플로가 멈춥니다). `$` 등 다른 문자는 그대로 쓰입니다.

### 4. 배포 뒤 nginx

monitoring-deploy 가 처음 끝나면 t3.large 의 옛 Grafana 가 지워지므로, 바로 t3.large 에서 nginx 를 다시 설치해 `grafana.<도메인>` 을 medium 으로 넘깁니다(그 전까지 502).
서버의 `~/nginx` 는 예전에 올린 옛 스크립트라 `MONITORING_HOST` 를 모릅니다. **새 `deploy/nginx` 를 먼저 다시 올립니다.**

```bash
scp -r deploy/nginx ec2-user@<t3.large 탄력적 IP>:~/          # 내 PC 에서
cd ~/nginx && sudo MONITORING_HOST=<t3.medium 사설 IP> ./install.sh kidly.kro.kr <이메일>   # t3.large 에서 (당시 방식. 지금 nginx 는 k3s Pod 라 deploy/nginx/README.md 의 deploy.sh 를 쓴다, #1309)
```

옛 데이터는 옮기지 않습니다. 확인이 끝나면 t3.large 에서 지웁니다.

```bash
docker volume rm monitoring_prometheus-data monitoring_loki-data monitoring_grafana-data
```

## 설치

`.github/workflows/monitoring-deploy.yml` 이 두 서버에 차례로 배포합니다. 둘 다 서버의 `/opt/app/monitoring` 입니다.

1. `server`: 이 폴더와 Secret 파일(`secrets/<키>`)을 t3.medium 에 올리고 `deploy.sh` 가 `kubectl apply --server-side -k .` 뒤 Pod 가 준비될 때까지 기다립니다. 데이터는 PVC(local-path, 노드 디스크)라 그대로 남습니다
2. `agent`: `deploy/monitoring-agent` 와 `.env`(`MONITORING_HOST`)를 t3.large 에 올리고 그 `deploy.sh` 가 Alloy 를 다시 띄웁니다

`develop` 에 `deploy/monitoring/**` · `deploy/monitoring-agent/**` 변경이 머지되면 자동으로 배포됩니다.
손으로 돌리려면 GitHub Actions → monitoring-deploy → Run workflow 를 누릅니다(`gh workflow run monitoring-deploy.yml --ref develop`).

