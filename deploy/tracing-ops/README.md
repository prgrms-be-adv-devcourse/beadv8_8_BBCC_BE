# 운영 트레이싱 준비

Tempo와 Grafana 데이터소스는 모니터링 배포에 포함됩니다. 사설 수신은 기본적으로 꺼져 있으며 앱 Agent는 수동 시범용입니다.

- `kubectl kustomize deploy/tracing-ops`로 모니터링 클러스터에 적용할 매니페스트를 확인합니다.
- Tempo는 ClusterIP의 조회 포트만 열고, 같은 네임스페이스의 Grafana Pod만 허용합니다. OTLP는 Tempo 컨테이너의 루프백에 바인딩합니다. 기존 Grafana Pod에서 Tempo 준비 상태와 조회 접근을, 비대상 Pod에서 접근 차단을 각각 확인합니다.
- 배포 후 기존 운영 계정으로 **Explore → Tempo**를 열고 조회 가능 여부와 기존 Loki·Prometheus 연결을 확인합니다. 수집 경로와 Agent가 준비되면 실제 주문 트레이스를 확인합니다.
- 보관 정책은 2시간이며 일반 Pod 교체 시 임시 데이터가 지워집니다. 메모리 상한 256MiB, 임시 볼륨 한도 384MiB를 두고 실제 자원 여유를 확인합니다.
- 다음 단계에서 클러스터 간 사설 OTLP 수집 경로와 Gateway·주문 Agent 배포를 검증합니다. 첫 서비스가 안정화되면 나머지 도메인에 순차 적용합니다.

## 앱 패치 미리보기

Gateway와 주문 Deployment의 현재 JSON을 `python3 deploy/tracing-ops/agent-patch.py --java-tool-options='<현재 JVM 옵션>'`의 표준 입력으로 넣으면 strategic merge 패치를 출력합니다. 생성기는 클러스터에 연결하지 않습니다. 입력은 승인된 대상에서 방금 읽은 Deployment 명세를 사용합니다. 출력에 현재 resourceVersion을 포함해 이후 배포 변경과 충돌하면 다시 읽어야 합니다.

- 현재 배포에 사용한 Compose 설정에서 민감정보가 없는 `JAVA_TOOL_OPTIONS` 값만 확인해 인자로 전달합니다. 앱의 기존 Secret 참조는 유지하고, 같은 옵션에 Agent를 붙인 값을 명시적인 환경 변수로 설정합니다. Secret 파일을 출력하거나 전달할 필요가 없습니다. JAR은 #1127에서 검증한 버전과 SHA-256을 대조한 뒤 모든 대상 노드에 읽기 전용으로 준비합니다.
- 수집 목적지는 계획된 앱 클러스터 내부 서비스 `tracing-alloy.kidly-dev.svc:4318`입니다. Alloy가 준비되고 모니터링 쪽과 사설 전송이 검증될 때 적용합니다.
- 공개 추적 헤더 제거가 운영에 반영된 뒤 5% 샘플링으로 시작합니다. 로그·메트릭 exporter와 Kafka 자동 계측은 끕니다.
- 적용 전 명세를 보관하고 운영 배포와 실행 시간이 겹치지 않게 조율합니다. 원복할 때는 현재 Deployment JSON에 같은 JVM 옵션과 `--rollback`을 전달합니다. 계측 당시 추가한 필드만 지우고 다른 배포 필드는 유지합니다. 값이 달라 원복 도구가 중단되면 적용 전 명세와 현재 명세를 비교합니다. 이 수동 패치는 이후 일반 배포가 덮어쓸 수 있습니다.

## 일반 앱 배포에서 유지

운영 시범에서 서비스별 실제 트레이스와 자원 사용량을 확인한 뒤에만 앱 서버의 `/opt/app/dev/operations/tracing-agent/`에 `gateway.enabled` 또는 `order.enabled`를 둡니다. 일반 배포는 선택 파일이 있는 서비스에 현재 Compose JVM 옵션과 같은 Agent 설정을 붙입니다. 배포 파일 업로드는 이 경로를 보존합니다. Agent JAR이 없거나 비어 있으면 해당 서비스 명세를 적용하기 전에 실패합니다.

원복은 선택 파일을 먼저 지운 뒤 일반 배포로 진행합니다. 수동 시범 패치가 이미 필드를 소유하고 있으면 일반 배포 뒤에도 해당 필드가 남을 수 있으므로, 현재 명세를 읽고 위의 `--rollback` 도구로 제거합니다. Pod가 준비된 뒤 Agent 환경 변수와 마운트가 사라졌는지 확인합니다.

## 앱 클러스터 수집기

`kubectl kustomize deploy/tracing-ops/app-alloy`로 Agent와 사설 수신점 사이의 Alloy 구성을 확인합니다. 기존 로그·메트릭 Alloy와 별도입니다.

- 앱 클러스터의 `kidly-dev`에서 Gateway·주문 Pod만 Alloy OTLP ClusterIP에 접근하도록 구성합니다. 수신된 span은 두 서비스의 HTTP·DB로 제한하고 허용된 속성만 남깁니다.
- 모니터링 쪽 수신기에 상호 TLS를 구성한 뒤, 사설 HTTPS 주소를 `tracing-tempo-endpoint` ConfigMap의 `url`에 지정합니다. `tracing-alloy-client-tls` Secret에는 해당 CA와 클라이언트 인증서·키를 준비합니다. 수신기는 HTTPS와 클라이언트 인증을 모두 검증해야 합니다.
- 모니터링 클러스터에는 `kubectl kustomize deploy/tracing-ops/private-receiver`로 상호 TLS 수신 설정을 렌더링합니다. `tracing-tempo-server-tls`에는 서버 인증서·키와 클라이언트 검증 CA를 준비합니다. 이 오버레이는 4318을 컨테이너 포트로만 두고 Service와 NetworkPolicy에 수신 접근을 추가하지 않습니다. 배포 전에 앱 클러스터에서만 접근할 수 있는 사설 주소와 실제 송신 원본에 맞는 정책을 설정해야 합니다.
- 기본 Tempo 조회 구성의 OTLP는 루프백에 있습니다. 수신 오버레이와 사설 경로가 준비된 뒤 Alloy를 적용하고 기존 Prometheus·Loki 수집과 분리해 검증합니다.

## 두 클러스터 사이 사설 경로

`python3 deploy/tracing-ops/private-route.py monitoring --app-source <사설-송신-IPv4>`와 `python3 deploy/tracing-ops/private-route.py app --tempo-node <Tempo-노드-사설-IPv4>`로 서로 다른 클러스터에 적용할 JSON을 미리 생성합니다. 이 도구는 현재 컨텍스트나 서버를 변경하지 않습니다.

1. 모니터링 노드 TCP 30438이 비어 있고, 클라우드 보안 그룹에서 앱 노드의 승인된 사설 경로만 허용하는지 확인합니다. NodePort 자체는 인터페이스를 사설 주소로 제한하지 않습니다. 공개 네트워크에서 열려 있으면 적용을 중단합니다.
2. Tempo 서버 인증서의 IP SAN에 목적지 사설 IPv4를 넣고 상호 TLS 수신 오버레이를 준비합니다. 모니터링 리소스와 앱 ConfigMap을 각각 맞는 클러스터에 적용합니다. `externalTrafficPolicy: Local`이므로 Tempo가 실행되는 노드로 연결합니다.
   인증서와 사설 정책이 준비되면 모니터링 서버의 `/opt/app/monitoring/operations/tempo-private-receiver.enabled`를 생성해 후속 모니터링 배포에서도 같은 수신 구성을 선택하도록 합니다.
3. 실제 Alloy Pod 출발점에서 전송 성공, 인증서 없는 연결 거부, 허용하지 않은 송신점에서 접근 차단을 확인합니다. CNI에 따라 NetworkPolicy에 보이는 원본이 다를 수 있으므로 한 주소의 /32 정책이 확인되기 전에는 앱 Agent를 연결하지 않습니다.
4. Traefik의 공개 추적 헤더 제거를 확인한 뒤 Gateway·주문 Agent를 적용합니다. 운영 Grafana에서 실제 주문 조회 트레이스와 기존 Loki 요청 ID 검색을 확인합니다.
5. 원복할 때 앱 Agent와 전용 Alloy를 내리고, 별도로 만든 `tempo-otlp-private` Service·NetworkPolicy와 외부 방화벽 허용을 제거합니다. 선택 파일을 제거한 뒤 기본 Tempo 조회 구성을 다시 적용합니다.

## 사설 수신기 준비

`tracing-stage-transport`는 운영 작업 시간에 TCP 30438 인바운드의 앱 서버 전용 규칙을 확인한 뒤 수동으로 실행합니다. 각 클러스터에서 노드 사설 IPv4를 하나씩 확인합니다. 기존 Tempo 사설 수신기, 인증서, 전용 Alloy 또는 Gateway·주문 선택 파일이 있으면 중단합니다. 새 상호 TLS 인증서는 시범용 7일이며, CA 개인키는 실행이 끝날 때 삭제합니다. 실패하면 이번 실행에서 만든 리소스를 정리하고 기본 Tempo 수신 구성으로 복귀합니다. Gateway와 주문 Deployment는 건드리지 않습니다.

## 사설 수신기 연결 확인

앱 서버의 발신 위치에서 `python3 verify-transport.py <Tempo 사설 IPv4> --ca <CA 인증서> --cert <Alloy 인증서> --key <Alloy 키>`로 Tempo의 TLS 수신기를 점검합니다. Tempo 서버 인증서의 IP SAN에 수신 노드의 사설 IPv4가 들어 있어야 합니다. 인증서가 있을 때 비어 있는 OTLP 요청이 성공하고, 같은 주소에 인증서 없이 접속하면 TLS에서 거절돼야 통과합니다. TCP 재설정처럼 거절 원인을 확인할 수 없으면 실패로 처리합니다. 요청에 span 데이터가 없어 Grafana에 메모나 트레이스를 남기지 않습니다. Alloy Pod의 실제 전송과 운영 요청 추적은 별도 확인 항목입니다.

## Alloy에서 Tempo까지 확인

수신 경로를 준비한 뒤 `tracing-verify-alloy`를 수동 실행합니다. 전용 Alloy에 합성 span 한 개를 넣고 Tempo에서 같은 ID를 조회합니다. 실제 요청과 구분되는 합성 계측 scope를 사용하며 앱이나 DB를 변경하지 않습니다. 운영 Grafana에서 실제 요청의 Gateway·주문·DB 추적은 Agent 적용 후 확인합니다.

## 운영 적용·원복 점검

`tracing-preflight`에서 `applied` 또는 `rolled-back`을 선택합니다. Gateway·주문 롤아웃, Agent 설정, 전용 Alloy, Tempo 수신 설정을 읽어서 기대 상태와 다르면 실패합니다. 이 검사는 설정과 Pod 준비 상태를 확인합니다. 실제 전송·사설 접근 차단과 Grafana의 주문 트레이스는 시범 절차에서 각각 확인합니다.

시험 비교에는 시작·종료 시각(KST), 환경, 대상 서비스와 요청 경로를 기록합니다. 같은 구간의 초당 요청 수, 5xx, 평균 응답 시간, DB 연결 대기, Pod CPU·메모리를 기존 대시보드에서 확인합니다. 지표가 조회되지 않으면 `데이터 없음`으로 적고 마지막에 Grafana 시간 범위 링크를 남깁니다.

## 운영 사전 점검

GitHub Actions에서 `tracing-preflight`를 develop 기준으로 수동 실행합니다. 두 클러스터의 Gateway·주문·Grafana·Tempo 준비 상태와 수집 리소스 유무를 확인합니다. 앱 환경 변수, Secret, 인증서 내용과 로그 원문은 출력하지 않습니다. 수집 경로가 준비된 이후에는 실제 요청의 Tempo 조회를 별도로 확인합니다.

## 부하 시험 관측

시험 시작·종료 시각(KST), 요청 경로, 요청률을 기록합니다. Agent 적용 전후 같은 요청 조건에서 비교하며 Grafana 시간 범위는 시험 앞뒤를 포함하도록 고정합니다. 환경은 실제 운영에 연결된 **운영**을 고릅니다.

| 화면 | 볼 항목 |
| --- | --- |
| [Kidly 서비스 오류](/d/kidly-http) | 초당 요청 수, 5xx 비율, 평균 응답 시간. Gateway와 대상 앱은 각각 확인합니다. 같은 요청을 두 서비스의 처리량으로 합산하지 않습니다. |
| [Kidly 로그](/d/kidly-logs) | **API 처리 시간**에서 로그 기반 p95를 확인합니다. 종료 시각 직전 30분에 경로별 20건 이상이 필요합니다. 시험 구간만의 p95는 부하 도구 결과에서 확인합니다. |
| [Kidly 응답·DB 연결](/d/kidly-db-latency) | 앱 DB 풀 사용률, 연결 대기, 시간 초과. |
| [Kidly 서버](/d/kidly-server) | 서버 CPU·가용 메모리·네트워크와 대상 Pod CPU·메모리. |
| [Kidly Kafka 처리](/d/kidly-kafka) | 이벤트를 발생시키는 시험에서만 관련 그룹·토픽의 미처리를 확인합니다. |
| Explore → Tempo | 같은 시간대에서 대표 요청의 서비스 간 흐름과 HTTP·DB 시간을 확인합니다. 5% 샘플을 전체 처리량 집계에 쓰지 않습니다. |

## Java Agent 파일 준비

`tracing-stage-agent`를 develop 기준으로 수동 실행하면 현재 앱 노드의 `/opt/app/kidly-tracing` 아래에 고정 버전 JAR을 준비합니다. runner와 앱 노드에서 모두 SHA-256을 확인하고, 기존 파일의 체크섬이 다르면 덮어쓰지 않고 중단합니다. 앱 Deployment를 변경하거나 Pod를 다시 띄우는 단계는 없습니다. 노드가 추가되면 배치 가능한 각 노드에 같은 파일이 있는지 확인합니다.
