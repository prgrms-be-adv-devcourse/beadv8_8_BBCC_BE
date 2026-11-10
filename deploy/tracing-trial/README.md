# dev 분산 트레이싱 시험 설정

#1127에서 준비하는 수동 시험 설정이다. 일반 앱·모니터링 배포에 포함되지 않는다. 공용 수집 경로, 헤더 정책과 데이터 필터가 검증되기 전에는 dev에 적용하지 않는다.

- Java Agent v2.33.1 공식 릴리스의 `opentelemetry-javaagent.jar`를 사용한다. SHA-256은 `e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149`다. 확인한 JAR의 절대 경로를 `TRIAL_AGENT_JAR`로 전달한다.
- dev Compose 프로젝트에만 기존 Compose와 이 override를 명시한다. `TRIAL_ALLOY_TRACES_ENDPOINT`는 검증된 비공개 OTLP HTTP 주소와 `/v1/traces`를 포함해야 한다. 샘플링 기본값 5%는 시험용 후보이며 통제된 기능 확인에서만 일시적으로 높인다.
- Tempo는 ClusterIP와 임시 저장소를 사용한다. Pod 삭제 시 시험 트레이스가 사라진다. 256MiB 메모리 제한은 실측 전 상한이다. Tempo 재시작이나 기존 수집에 영향이 보이면 시험을 중단한다.

남은 적용 조건은 앱 서버 Alloy에서 Tempo까지 제한된 전송 경로, Alloy 속성 필터, 공개 ingress의 외부 추적 헤더 처리, Grafana 임시 데이터소스다. 기존 요청 ID는 유지한다. 원복 시 dev 앱 두 컨테이너를 기본 Compose로 다시 만들고, 시험 수집 경로와 데이터소스 및 Tempo 리소스를 제거한다.
