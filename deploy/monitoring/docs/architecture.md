# 모니터링 구성·제약

[운영 안내로 돌아가기](../README.md)

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

## 알려진 한계

- **t3.medium 이 죽으면 알림이 모두 멈춥니다.** Grafana 가 알림을 보내므로 medium 이 꺼지거나 Grafana · Prometheus 가 죽으면 알릴 수 없습니다. 감지되는 것은 `monitoring-deploy` 실패 알림(`#4팀-알림`)뿐입니다. 반대로 t3.large 가 통째로 죽으면 "수집 끊김" 이 울립니다.
- **18시 EC2 자동 정지는 운영 의도를 기록하지 않습니다.** 사전에 운영 중지 절차를 연결하지 않으면 장애로 감지합니다. 자동 정지·24시간 운영 정책은 #775에서 다룹니다.
- **NodePort(30090 · 31100)와 Traefik(80)은 노드의 모든 주소에 열립니다.** 막는 것은 보안 그룹(소스 `team04-sg`)뿐이라, 이 보안 그룹을 넓히면 인증 없는 Prometheus · Loki 쓰기가 밖에 열립니다.
- **t3.large Alloy 는 보내기에 실패해도 배포가 성공합니다.** 보안 그룹이 막혀 있어도 Alloy 는 준비 상태가 됩니다. "수집 끊김" 알림과 위 `docker compose ... logs alloy` 로 알아챕니다.
- **앱이 막 재시작한 직후의 ERROR 는 건수 알림에서 빠질 수 있습니다.** 첫 수집(15초) 전에 난 ERROR 는 카운터에 잡히지 않습니다. 로그(Loki)에는 남습니다.
- **Alloy 는 로그를 읽으려고 `/var/run/docker.sock` 을 마운트합니다.** `:ro` 여도 Docker API 는 막지 못해, Alloy 가 침해되면 서버 root 와 같은 권한이 됩니다. 127.0.0.1 에만 열고 원격 설정을 쓰지 않는 것으로 위험을 줄였습니다. 더 줄이려면 `docker-socket-proxy` 로 `containers` · `events` 만 열 수 있습니다. 컨테이너 메트릭(cAdvisor)을 위해 `privileged` 로 돌고 호스트 `/` · `/proc` · `/sys` 를 읽기 전용으로 마운트하는데, docker.sock 으로 이미 같은 권한이라 더 넓어지는 것은 아닙니다.
- **컨테이너 메트릭은 로컬(Docker Desktop)에서는 안 나옵니다.** Docker Desktop 은 containerd 이미지 저장소를 써서 cAdvisor 가 컨테이너를 못 읽습니다(`failed to identify the read-write layer ID`). 서버(overlay2)에서는 나옵니다. 호스트 메트릭은 로컬에서도 Docker VM 기준으로 나옵니다.


