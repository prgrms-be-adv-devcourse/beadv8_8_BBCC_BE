# 운영 트레이싱 준비

Tempo 조회용 리소스와 Grafana 데이터소스는 기본 모니터링 배포에 포함됩니다. 이 디렉터리의 수집 오버레이와 앱 Agent는 수동 시범용입니다.

- `kubectl kustomize deploy/tracing-ops`로 모니터링 클러스터에 적용할 매니페스트를 확인합니다.
- Tempo는 ClusterIP의 조회 포트만 열고, 같은 네임스페이스의 Grafana Pod만 허용합니다. OTLP는 Tempo 컨테이너의 루프백에 바인딩합니다. 기존 Grafana Pod에서 Tempo 준비 상태와 조회 접근을, 비대상 Pod에서 접근 차단을 각각 확인합니다.
- 배포 후 기존 운영 계정으로 **Explore → Tempo**를 열고 조회 가능 여부와 기존 Loki·Prometheus 연결을 확인합니다. 수집 경로와 Agent가 준비되면 실제 주문 트레이스를 확인합니다.
- 보관 정책은 2시간이며 일반 Pod 교체 시 임시 데이터가 지워집니다. 메모리 상한 256MiB, 임시 볼륨 한도 384MiB를 두고 실제 자원 여유를 확인합니다.
- 다음 단계에서 클러스터 간 사설 OTLP 수집 경로와 Gateway·주문 Agent 배포를 검증합니다. 첫 서비스가 안정화되면 나머지 도메인에 순차 적용합니다.

## 앱 패치 미리보기

Gateway와 주문 Deployment의 현재 JSON을 `python3 deploy/tracing-ops/agent-patch.py --java-tool-options='<현재 JVM 옵션>'`의 표준 입력으로 넣으면 strategic merge 패치를 출력합니다. 생성기는 클러스터에 연결하지 않습니다. 입력은 배포 산출물 또는 승인된 대상에서 읽어온 명세를 사용합니다.

- 현재 배포에 사용한 Compose 설정에서 민감정보가 없는 `JAVA_TOOL_OPTIONS` 값만 확인해 인자로 전달합니다. 앱의 기존 Secret 참조는 유지하고, 같은 옵션에 Agent를 붙인 값을 명시적인 환경 변수로 설정합니다. Secret 파일을 출력하거나 전달할 필요가 없습니다. JAR은 #1127에서 검증한 버전과 SHA-256을 대조한 뒤 모든 대상 노드에 읽기 전용으로 준비합니다.
- 수집 목적지는 계획된 앱 클러스터 내부 서비스 `tracing-alloy.kidly-dev.svc:4318`입니다. Alloy가 준비되고 모니터링 쪽과 사설 전송이 검증될 때 적용합니다.
- 공개 추적 헤더 제거가 운영에 반영된 뒤 5% 샘플링으로 시작합니다. 로그·메트릭 exporter와 Kafka 자동 계측은 끕니다.
- 적용 전 명세를 보관하고 운영 배포와 실행 시간이 겹치지 않게 조율합니다. 원복은 기존 명세로 복원합니다. 이 수동 패치는 이후 일반 배포가 덮어쓸 수 있습니다.

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
3. 실제 Alloy Pod 출발점에서 전송 성공, 인증서 없는 연결 거부, 허용하지 않은 송신점에서 접근 차단을 확인합니다. CNI에 따라 NetworkPolicy에 보이는 원본이 다를 수 있으므로 한 주소의 /32 정책이 확인되기 전에는 앱 Agent를 연결하지 않습니다.
4. Traefik의 공개 추적 헤더 제거를 확인한 뒤 Gateway·주문 Agent를 적용합니다. 운영 Grafana에서 실제 주문 조회 트레이스와 기존 Loki 요청 ID 검색을 확인합니다.
5. 원복할 때 앱 Agent와 전용 Alloy를 내리고, 별도로 만든 `tempo-otlp-private` Service·NetworkPolicy와 외부 방화벽 허용을 제거합니다. 이후 기본 Tempo 조회 구성을 다시 적용합니다.

## 운영 사전 점검

GitHub Actions에서 `tracing-preflight`를 develop 기준으로 수동 실행합니다. 두 클러스터의 Gateway·주문·Grafana·Tempo 준비 상태와 수집 리소스 유무를 확인합니다. 앱 환경 변수, Secret, 인증서 내용과 로그 원문은 출력하지 않습니다. 수집 경로가 준비된 이후에는 실제 요청의 Tempo 조회를 별도로 확인합니다.
