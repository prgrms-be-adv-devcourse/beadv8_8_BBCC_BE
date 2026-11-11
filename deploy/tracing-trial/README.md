# 격리 k3s 분산 트레이싱 시험

Gateway·주문을 운영하는 kidly-dev와 별도의 폐기 가능한 시험 클러스터에서 사용한다. 이 디렉터리는 일반 배포에 포함되지 않는다.

## 준비

- 시험 클러스터의 kidly-tracing-trial 네임스페이스에 Gateway·주문 Deployment와 ClusterIP Service를 준비한다. Pod 템플릿에는 각각 app=gateway, app=order 라벨을 붙인다. 운영과 같은 앱 이미지와 JVM 옵션을 사용하고 DB·Kafka·인증은 시험 전용으로 구성한다. 합성 구매자와 주문을 준비한다.
- Alloy와 Tempo를 설치하기 전에 네트워크 정책이 강제되는지 확인한다. 이 구성은 Gateway·주문 Pod에서 Alloy로, Alloy에서 Tempo 수집 포트로, 시험 Grafana에서 Tempo 조회 포트로 연결한다. 시험 Grafana는 같은 네임스페이스에 app=grafana 라벨을 사용하고 grafana-datasource.yml을 읽는다.
- 시험 노드에 Java Agent v2.33.1 공식 JAR을 설치한다. SHA-256은 e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149다. 검증한 파일을 /opt/kidly-tracing/opentelemetry-javaagent.jar에 두고 앱 UID가 읽을 수 있게 한다. 여러 노드에서 시험하면 두 앱이 배치될 수 있는 모든 노드에 같은 파일을 설치한다.
- 시험 컨텍스트와 API 서버 주소를 먼저 대조한다. 네임스페이스의 앱 Deployment 이름 및 컨테이너 이름은 gateway, order다. 두 앱의 기존 JAVA_TOOL_OPTIONS가 -Xmx256m인지 확인한다.

## 실행 및 원복

1. Agent 없이 워밍업 후 주문 상세 GET을 동시성 1, 요청 사이 1초, 30회 조건으로 실행한다. 성공 TPS, p50, p95, 실패율과 CPU·메모리를 기록한다.
2. 시험 컨텍스트에서 이 디렉터리의 Kustomize 리소스를 적용한다. 시험 Grafana와 비대상 Pod에서 각각 Tempo 접근 허용·차단을 확인한다. 클라이언트는 시험 Gateway의 로컬 port-forward로만 접근한다.
3. agent-patch.py에 gateway 또는 order를 넘겨 패치를 생성하고, 명시한 시험 컨텍스트와 kidly-tracing-trial 네임스페이스의 해당 Deployment에 strategic merge patch를 적용한다. 기능 확인 동안만 --full-sampling을 사용한다.
4. 정상과 404 주문 상세에서 Gateway·주문 HTTP와 JDBC가 한 트레이스에 나타나는지 확인한다. 합성 URL·SQL 값과 민감 속성이 저장되지 않는지 확인한 뒤 기본 샘플링 5%로 같은 부하를 다시 실행한다. Tempo 자원·저장량·OTLP 전송량도 기록한다.
5. Agent를 붙이기 전의 시험 Deployment 명세를 다시 적용하거나 시험 클러스터를 폐기한다. 트레이스는 Tempo Pod 임시 저장소에 남으며 Pod 삭제 시 제거된다.
