# 모니터링 (Prometheus · Loki · Grafana)

앱의 메트릭과 로그, 서버 · 컨테이너 자원 사용량을 모으고 Grafana 로 봅니다. 앱 다운 · 5xx 증가 · ERROR 로그 · 수집 끊김은 Slack `#4팀-운영이슈` 로 알립니다.
모니터링은 앱과 다른 서버(t3.medium)에 둡니다. 앱 서버가 통째로 죽어도 알림을 보낼 수 있게 하기 위해서입니다.

| 서버 | 무엇이 | 어디 | 하는 일 | 보관 |
| --- | --- | --- | --- | --- |
| t3.medium (k3s) | Prometheus | Service `prometheus:9090`, NodePort `30090` | Alloy 가 remote write 로 넣는 메트릭 저장. 직접 수집하지 않음 | 15일 |
| | Loki | Service `loki:3100`, NodePort `31100` | 로그 저장 | 14일 |
| | Grafana | Service `grafana:3000`, Ingress `grafana.kidly.kro.kr` | 대시보드 · 로그 검색 · 알림 | - |
| | Alloy (DaemonSet) | 노드 `127.0.0.1:12345` | medium 노드 메트릭 · 이 노드 Pod 로그 | - |
| t3.large (docker) | Alloy (`deploy/monitoring-agent`) | `127.0.0.1:12345` | 앱 `/actuator/prometheus` 수집(아래 수집 대상), dev · prod 앱 로그, 내장 node_exporter · cAdvisor 로 호스트 · 컨테이너 자원. 모두 medium 으로 보냄 | - |

- 밖에서는 t3.large nginx 의 `https://grafana.<도메인>` 으로 Grafana 만 보입니다. nginx 가 medium 의 Traefik(80)으로 넘깁니다.
- NodePort 와 Traefik 80 은 노드의 모든 주소에 열립니다. **보안 그룹이 t3.large(`team04-sg`)에서 오는 것만 받게 해서 막습니다.**
- 모니터링 워크로드는 노드 라벨 `kidly/role=infra` 인 노드(medium)에만 뜹니다.

데이터소스 · 대시보드 · 알림 규칙 · Slack 연결은 `grafana/` 아래 파일로 관리합니다. 배포 때 ConfigMap 이 됩니다(`kustomization.yaml`).
JVM 대시보드(`grafana/dashboards/jvm-micrometer.json`)는 커뮤니티 대시보드 [4701](https://grafana.com/grafana/dashboards/4701) 리비전 10 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` 라벨 조건을 `job="kidly"` 로 바꾼 것입니다.
Spring Boot 대시보드(`grafana/dashboards/spring-boot.json`, HikariCP · HTTP · GC · Logback)는 커뮤니티 대시보드 [19004](https://grafana.com/grafana/dashboards/19004) 리비전 1 을 받아
데이터소스를 uid `prometheus` 로 고정하고, 이 앱에 없는 `application` · `namespace` 변수와 `instance` 변수를 빼고 `env` 변수 하나로 거르게(`job="kidly", env="$env"`) 바꾼 것입니다.
INFO logs 패널에 섞여 있던 전체 레벨 누적값 쿼리도 뺐습니다.
서버 대시보드(`grafana/dashboards/kidly-server.json`)는 호스트 CPU · 메모리 · 디스크 · 업타임과 디스크 I/O · 네트워크, 컨테이너별(앱 · Postgres · Kafka · Alloy) CPU · 메모리를 한 화면에 보여 줍니다.
위의 **서버** 변수로 t3.large · t3.medium 을 고릅니다(값은 호스트 이름). 컨테이너 패널은 t3.large 에만 있습니다(medium 은 컨테이너 메트릭을 모으지 않습니다).

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
cd ~/nginx && sudo MONITORING_HOST=<t3.medium 사설 IP> ./install.sh kidly.kro.kr <이메일>   # t3.large 에서
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

## 확인

t3.medium 에서 deploy 유저로(`sudo -u deploy -i`):

```bash
kubectl -n monitoring get pods                                                  # 모두 Running · READY 1/1
curl -s localhost:30090/api/v1/query --data-urlencode 'query=up{job="kidly"}'   # env 마다 "1"
curl -s localhost:31100/ready                                                   # ready
```

`https://grafana.<도메인>` 에 `admin` 으로 로그인해 Kidly 폴더의 대시보드를 봅니다.
알림 → 연락처 지점 → `slack-ops` 의 "Test" 로 Slack 수신을 확인합니다.

#453 이전 커밋(롤백 · 오래된 기능 브랜치)을 dev 에 올리면 `/actuator/prometheus` 가 없어 "앱 다운" 이 울립니다. 그동안은 Grafana 에서 그 규칙을 일시정지합니다.

## 알림 메시지

Slack 메시지 형식은 `grafana/provisioning/alerting/templates.yml` 에 있습니다. 제목은 알림 이름 · 환경, 본문은 규칙의 `summary` · `description` 입니다.

```
🔴 앱 다운 · dev                        ← warning 은 🟠 주황, 그 밖은 빨강. 같은 그룹에 여러 건이면 끝에 (N건)
앱 메트릭을 2분 넘게 못 읽었습니다 (앱 다운 또는 /actuator/prometheus 없음)
규칙 보기 · 일시정지

🟠 ERROR 로그 · dev
최근 5분 ERROR 로그 3건
규칙 보기 · 일시정지 · 로그 보기

🟢 해결 · 앱 다운 · dev                  ← 초록
정상으로 돌아왔습니다
규칙 보기
```

Prometheus 조회가 실패하면 알림 이름이 `DatasourceError` 로 오는데, 제목에는 규칙 이름을 써서 `🔴 앱 다운 · 조회 실패` 로 보입니다(데이터가 없을 때는 `· 데이터 없음`).

"일시정지" 는 그 알림의 라벨로 silence 를 만드는 화면을 엽니다.
5xx 증가 · ERROR 로그 알림에는 "로그 보기" 가 더 붙습니다. 그 환경의 최근 15분 5xx 요청 로그 · ERROR 로그를 Explore 로 엽니다(규칙의 `logs_url`).

## 설정 바꾸기

레포의 파일을 고쳐 `develop` 에 머지하면 워크플로가 다시 배포합니다. 레포에서 지운 설정 파일은 서버에서도 지워집니다.

비밀을 바꿀 때는 Secret 을 고친 뒤(`gh secret set ... --env dev`) 워크플로를 손으로 실행합니다.
**Grafana 관리자 비밀번호는 Secret 이 원본입니다. Grafana 화면에서 바꾸지 마세요.** 배포마다 Secret 값으로 맞춰지므로 화면에서 바꾼 비밀번호는 다음 배포 때 되돌아갑니다.

Grafana 화면에서 고친 대시보드 · 알림 규칙은 저장되지 않거나 다음 배포 때 파일 내용으로 돌아갑니다. 바꿀 것은 레포에서 고칩니다.

설정 ConfigMap · Secret 은 이름에 내용 해시가 붙어, 바뀌면 그것을 쓰는 Pod 가 다시 뜹니다. 배포는 지우지 않으므로(prune 안 함) 옛 해시의 ConfigMap · Secret 이 남습니다. 비밀을 바꾼 뒤에는 옛 Secret(`kubectl -n monitoring get secret`, 지금 Grafana 가 쓰지 않는 `monitoring-secrets-*`)을 지웁니다. 레포에서 매니페스트(Deployment 등)를 지웠을 때도 클러스터에서는 손으로 지웁니다(`kubectl -n monitoring delete ...`).

## 수집 대상

앱 배포(`deploy/deploy.sh`)가 앱이 healthy 로 뜰 때마다 `/opt/app/monitoring/targets/<환경>.yml` 에 `127.0.0.1:<APP_PORT>` 와 `env` 라벨을 써 둡니다.
t3.large 의 Alloy 가 이 폴더를 읽어(`discovery.file`) 대상을 바꾸므로 손으로 고칠 것이 없습니다. 수집한 메트릭은 job `kidly` · `env` · `instance`(`127.0.0.1:<포트>`) 라벨로 medium Prometheus 에 들어갑니다. prod 도 첫 배포 때 수집 · 알림이 시작됩니다.
기동에 실패한 배포는 파일을 바꾸지 않습니다. 이미 있던 환경이면 "앱 다운" 이 그대로 울립니다.
검색이 켜진 환경(지금은 dev)은 검색 서비스도 `targets/search/<환경>.yml` 에 `127.0.0.1:<SEARCH_PORT>` 로 써 둡니다. 앱 대시보드 · 알림과 섞이지 않게 job 을 `kidly-search` 로 나눠서, 앱 알림(job `kidly`)은 검색 서비스를 보지 않습니다.
로그는 Alloy 가 앱 · 검색 서비스 컨테이너를 알아서 찾습니다. 호스트 · 컨테이너 메트릭도 Alloy 가 모아 Prometheus 에 넣으므로(job `integrations/unix` · `integrations/cadvisor`) 등록할 것이 없습니다.
medium 의 Pod 로그는 `namespace` · `pod` · `container` · `service_name`(컨테이너 이름) 라벨로 들어갑니다(`env` 없음).

t3.large 에서:

```bash
cat /opt/app/monitoring/targets/*.yml /opt/app/monitoring/targets/search/*.yml
docker compose -p monitoring logs alloy --tail 50   # 보내기 실패(medium 에 닿지 못함)가 여기 남는다
```

환경을 없앨 때는 그 환경의 파일(`targets/<환경>.yml`, 검색이 켜져 있었으면 `targets/search/<환경>.yml` 도)을 지웁니다. 남겨 두면 "앱 다운" 이 계속 울립니다.

## 로그 보기

Kidly 폴더의 **Kidly 로그** 대시보드에서 상태 코드별 요청 수 · 요청 로그 · ERROR 로그 · 느린 요청(1초 넘음) · 요청 상세 표(시간 · method · route · status · duration · IP · memberId · traceId)를 봅니다.
직접 찾을 때는 Grafana → Explore → 데이터소스 Loki 에서 검색합니다. 결과 위의 **Table** 을 누르면 줄마다 한 행으로 필드를 열로 봅니다.

### Drilldown 으로 찾기

쿼리 없이 찾을 때는 Grafana → Drilldown → Logs → kidly 의 **Show logs** 를 누릅니다. 위 **Fields** 칸에서 필드 · 연산자 · 값을 골라 거르고, 여러 개를 넣으면 모두 맞는 줄만 나옵니다.

| 찾을 것 | Fields 칸 | 메모 |
| --- | --- | --- |
| 5xx | `http_response_status_code` `>= 500` (또는 `=~ 5..`) | 숫자 비교 · 정규식 둘 다 됩니다 |
| 특정 상태 | `http_response_status_code` `= 401` | |
| URL | `url_path` 또는 `http_route` `=~ .*orders.*` | 정규식은 값 전체와 맞아야 해서 부분 일치는 앞뒤에 `.*` |
| 회원 | 줄을 펼쳐 `memberId` 옆 돋보기(+) | 구조화 메타데이터라 `\| json` 앞에서 걸러 가볍습니다. Fields 칸의 `memberId = 7` 도 됩니다 |
| 한 요청 | 표의 `traceId` 를 누르면 "같은 요청 로그 보기"(Explore). 줄을 펼쳐 `traceId` 옆 돋보기(+) 도 됩니다 | |

위 **Line filter** 칸은 줄 전체에서 글자를 찾습니다(특정 필드가 아님).
결과 위 **Table** 을 누르면 아래 열이 기본으로 골라져 있습니다. 필터를 바꿔도 열은 그대로입니다. 앞의 Time · Line · detected_level 은 Drilldown 이 늘 붙이는 열입니다.

`http_request_method` · `http_route` · `url_path` · `http_response_status_code` · `duration_ms` · `client_ip` · `memberId` · `traceId` · `message`

기본 열의 원본은 `grafana/logs-drilldown-default-columns.json`(데이터소스 `loki`, `service_name=kidly`)입니다. 배포 때 `deploy.sh` 가 Grafana API 로 넣으므로 화면(관리 → 플러그인 → Logs Drilldown → Default fields)에서 바꾼 것은 다음 배포 때 파일 내용으로 돌아갑니다.
각자 열을 더 고르면 그 브라우저에만 남습니다. 이 기능은 Grafana 12.4 이상 · 기능 플래그 `kubernetesLogsDrilldown`(`k8s/grafana.yaml` 의 `GF_FEATURE_TOGGLES_ENABLE`)이 있어야 합니다.
Logs Drilldown 앱 버전은 `k8s/grafana.yaml` 의 `GF_PLUGINS_PREINSTALL` 로 고정합니다. 앱은 Grafana 가 뜬 뒤 백그라운드로 받아 오므로, 받지 못하면 배포는 성공하고 `::warning::` 만 남습니다(기본 열이 안 보이면 monitoring-deploy 를 다시 돌림).

**Grafana 를 올릴 때 확인**: 앱 버전(`GF_PLUGINS_PREINSTALL`), 기능 플래그 `kubernetesLogsDrilldown`(실험 기능), 기본 열 API 경로 `apis/logsdrilldown.grafana.app/v1beta1/.../logsdrilldowndefaultcolumns`(`deploy.sh`). 경로가 바뀌면 `deploy.sh` 가 실패해 `#4팀-알림` 에 모니터링 배포 실패가 옵니다 — 컨테이너는 이미 새로 떠 있어 수집 · 알림은 그대로입니다.

서버(dev · prod) 콘솔 로그는 ECS JSON 한 줄입니다. 라벨은 `env` · `level` · `service_name`(앱 `kidly`, 검색 서비스 `kidly-search`)이고, `traceId` · `memberId` 는 구조화 메타데이터라 `| traceId="..."` · `| memberId="..."` 로 거릅니다.
로그 줄을 펼치면 traceId 옆 "같은 요청 로그 보기" 링크로 그 요청의 로그를 모두 엽니다(Explore, 최근 24시간).
나머지 필드는 `| json` 으로 펼쳐 거릅니다. 점은 `_` 로 바뀝니다.

| JSON 키 | `\| json` 뒤 이름 | 예 |
| --- | --- | --- |
| `log.level` · `log.logger` | `log_level` · `log_logger` | `ERROR` · `com.bbcc.kidly...` |
| `message` | `message` | `GET /api/products - 200 (2ms)` |
| `http.request.method` | `http_request_method` | `GET` |
| `url.path` | `url_path` | `/api/products` |
| `http.response.status_code` | `http_response_status_code` | `200` |
| `duration_ms` | `duration_ms` | `2` |
| `http.route` | `http_route` | `/api/v1/products/{id}` |
| `url.query` | `url_query` | `page=1&token=***` |
| `client.ip` | `client_ip` | `203.0.113.7` |
| `user_agent.original` | `user_agent_original` | `Mozilla/5.0 ...` |
| `http.request.headers.<이름>` · `http.response.headers.<이름>` | `http_request_headers_<이름>` · `http_response_headers_<이름>` | 헤더 이름은 소문자 |
| `http.request.body.content` · `http.response.body.content` | `http_request_body_content` · `http_response_body_content` | JSON 본문, 각 UTF-8 3,000바이트 |
| `memberId` | (구조화 메타데이터) | `7` |
| `error.type` · `error.message` · `error.stack_trace` | `error_type` · `error_message` · `error_stack_trace` | 예외가 있을 때 |
| `process.thread.name` | `process_thread_name` | `http-nio-8080-exec-1` |

요청 로그는 요청이 끝날 때 `RequestLoggingFilter` 가 한 줄 남기고, 요청 필드(method · path · status · duration · IP · 헤더 · 본문 등)는 이 줄에만 있습니다. `traceId` 는 요청 안에서 남긴 로그에만, `memberId` 는 로그인한 요청 안에서 남긴 로그에만 있습니다.
가림: 헤더 `Authorization` · `Cookie` · `Set-Cookie` · `Proxy-Authorization` 과 이름에 아래 말이 든 헤더(`X-Auth-Token` · `X-Api-Key` 등), 쿼리 · JSON 본문의 비밀값(password · token · secret · apiKey · paymentKey · uploadUrl · 사업자등록번호 registrationNumber 계열), 연락처 · 주소(phone · email · address · zipCode 계열), 자녀 생년월일 · 키 · 몸무게(`birthDate` · `heightCm` · `weightKg`, 이름이 같을 때만) 값은 `***` 입니다. 이름 · ID · 금액은 남깁니다.
본문을 남기지 않는 것: `/api/v1/auth/**` · `/api/v1/dev/tokens` · `/api/v1/webhooks/toss`, JSON 이 아닌 본문(텍스트는 가릴 방법이 없어 생략, multipart · 바이너리), 비동기 응답. 64KB 가 넘는 응답 본문은 `(N바이트, 생략)` 만 남깁니다. 본문은 UTF-8 3,000바이트(`…(잘림, 전체 N바이트)`), 헤더 값은 300자(`…(잘림, 전체 N자)`)를 넘으면 잘립니다.
요청 로그 쿼리는 로거 이름으로 먼저 좁혀(`|= "RequestLoggingFilter"`) `| json` 할 줄을 줄이고, 요청 필드가 있는 줄만 고릅니다(`http_request_method != ""`).

```
{env="dev", level="ERROR"}                                        # ERROR 로그 (스택 트레이스 포함)
{env="dev"} | traceId="3e81d408-302d-44fa-8ee3-bcc817933f7b"      # 한 요청의 로그 전부
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | http_request_method != ""         # 요청 로그
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | http_response_status_code >= 500  # 5xx 요청
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | duration_ms > 1000                # 1초 넘은 요청
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | url_path = "/api/products"
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | http_route = "/api/v1/products/{id}"
{env="dev"} |= "RequestLoggingFilter" | json | __error__="" | client_ip = "203.0.113.7"     # 한 IP 의 요청
{env="dev"} | memberId="7"                                                                 # 한 회원의 로그 전부
sum by (http_response_status_code) (count_over_time({env="dev"} |= "RequestLoggingFilter" | json http_request_method="http.request.method", http_response_status_code="http.response.status_code" | __error__="" | http_request_method != "" [5m]))
```

인자 없는 `| json` 은 헤더 · 본문까지 모든 필드를 펼칩니다. 한 줄을 찾아 볼 때는 편하지만, 많은 줄을 세거나 대시보드처럼 자주 도는 쿼리는 필요한 필드만 뽑습니다(`| json 이름="JSON.경로", ...`, 위 마지막 줄 · Kidly 로그 대시보드).

수를 세는 쿼리(`count_over_time` 등)는 `| json` 뒤에 `__error__=""` 를 붙입니다. JSON 이 아닌 줄(기동 배너, JSON 이전 이미지의 텍스트 로그)이 섞이면 쿼리가 실패합니다.
JSON 이전 이미지의 텍스트 로그도 `level` 라벨로는 그대로 찾을 수 있고, 스택 트레이스는 로그 한 건으로 묶여 있습니다.

## 알려진 한계

- **t3.medium 이 죽으면 알림이 모두 멈춥니다.** Grafana 가 알림을 보내므로 medium 이 꺼지거나 Grafana · Prometheus 가 죽으면 알릴 수 없습니다. 감지되는 것은 `monitoring-deploy` 실패 알림(`#4팀-알림`)뿐입니다. 반대로 t3.large 가 통째로 죽으면 "수집 끊김" 이 울립니다.
- **"수집 끊김" 은 정상일 때도 한 번 울릴 수 있습니다.** 18시 자동 정지 때 t3.large 가 medium 보다 몇 분 먼저 꺼질 때, 어느 환경도 배포되지 않았을 때(수집 대상 없음), 처음 배포에서 medium 이 뜬 뒤 t3.large Alloy 가 붙기까지 5분이 넘을 때입니다.
- **NodePort(30090 · 31100)와 Traefik(80)은 노드의 모든 주소에 열립니다.** 막는 것은 보안 그룹(소스 `team04-sg`)뿐이라, 이 보안 그룹을 넓히면 인증 없는 Prometheus · Loki 쓰기가 밖에 열립니다.
- **t3.large Alloy 는 보내기에 실패해도 배포가 성공합니다.** 보안 그룹이 막혀 있어도 Alloy 는 준비 상태가 됩니다. "수집 끊김" 알림과 위 `docker compose ... logs alloy` 로 알아챕니다.
- **앱이 막 재시작한 직후의 ERROR 는 건수 알림에서 빠질 수 있습니다.** 첫 수집(15초) 전에 난 ERROR 는 카운터에 잡히지 않습니다. 로그(Loki)에는 남습니다.
- **Alloy 는 로그를 읽으려고 `/var/run/docker.sock` 을 마운트합니다.** `:ro` 여도 Docker API 는 막지 못해, Alloy 가 침해되면 서버 root 와 같은 권한이 됩니다. 127.0.0.1 에만 열고 원격 설정을 쓰지 않는 것으로 위험을 줄였습니다. 더 줄이려면 `docker-socket-proxy` 로 `containers` · `events` 만 열 수 있습니다. 컨테이너 메트릭(cAdvisor)을 위해 `privileged` 로 돌고 호스트 `/` · `/proc` · `/sys` 를 읽기 전용으로 마운트하는데, docker.sock 으로 이미 같은 권한이라 더 넓어지는 것은 아닙니다.
- **컨테이너 메트릭은 로컬(Docker Desktop)에서는 안 나옵니다.** Docker Desktop 은 containerd 이미지 저장소를 써서 cAdvisor 가 컨테이너를 못 읽습니다(`failed to identify the read-write layer ID`). 서버(overlay2)에서는 나옵니다. 호스트 메트릭은 로컬에서도 Docker VM 기준으로 나옵니다.
