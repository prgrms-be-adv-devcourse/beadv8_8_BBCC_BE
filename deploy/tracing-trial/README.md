# dev 분산 트레이싱 시험 설정

#1127에서 준비하는 수동 시험 설정이다. 일반 앱·모니터링 배포에 포함되지 않는다. 공용 수집 경로, 헤더 정책과 데이터 필터가 검증되기 전에는 dev에 적용하지 않는다.

- Java Agent v2.33.1 공식 릴리스의 `opentelemetry-javaagent.jar`를 사용한다. SHA-256은 `e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149`다. 확인한 JAR의 절대 경로를 `TRIAL_AGENT_JAR`로 전달한다.
- dev Compose 프로젝트에만 기존 Compose와 이 override를 명시한다. `TRIAL_ALLOY_TRACES_ENDPOINT`는 검증된 비공개 OTLP HTTP 주소와 `/v1/traces`를 포함해야 한다. 샘플링 기본값 5%는 시험용 후보이며 통제된 기능 확인에서만 일시적으로 높인다.
- Tempo는 ClusterIP와 임시 저장소를 사용한다. Pod 삭제 시 시험 트레이스가 사라진다. 256MiB 메모리 제한은 실측 전 상한이다. Tempo 재시작이나 기존 수집에 영향이 보이면 시험을 중단한다.

시험 시간에만 적용한다.

1. 모니터링 서버에서 Tempo를 띄운다. 기존 권한으로 모니터링 서버의 loopback에 Pod 또는 Deployment에 직접 kubectl port-forward를 열고, 앱 서버에서 기존 승인된 SSH 경로로 loopback 14318에 로컬 포워드를 연다. SSH 경로나 권한이 없으면 멈춘다. OTLP는 Pod loopback에서만 받는다. Grafana 조회용 ClusterIP에는 NetworkPolicy를 적용하고, 다른 Pod에서 접근이 차단되는지 dev에서 확인한다.
2. 앱 서버에서 dev Compose 브리지의 호스트 게이트웨이 주소와 접근 제한을 확인한다. dev 컨테이너만 수신 포트에 닿도록 제한한 뒤 TRIAL_OTLP_LISTEN을 그 주소와 포트로 지정하고 기존 Alloy 설정에 otlp.alloy를 합친다. 수신 데이터의 환경·서비스 이름은 필터용이며 인증 수단으로 쓰지 않는다.
3. dev nginx에서 외부 추적 헤더를 제거하는 설정을 검사하고 reload한다. Grafana의 시험 데이터소스는 제공한 파일 기준으로 임시 등록한다. 기존 Loki 데이터소스는 그대로 둔다.
4. dev Gateway·주문에 Compose override를 적용하고 정상·404 트레이스와 민감 속성 제거를 확인한다. 같은 부하로 p50·p95·실패율·성공 TPS를 비교한다.

원복은 dev 앱 두 컨테이너를 기본 Compose로 다시 만들고, 시험 Alloy 설정과 수신 제한, SSH 포워드, Grafana 데이터소스, Tempo 리소스를 제거한다. 기존 Loki·Prometheus 수집과 앱 정상 여부를 확인한다.
