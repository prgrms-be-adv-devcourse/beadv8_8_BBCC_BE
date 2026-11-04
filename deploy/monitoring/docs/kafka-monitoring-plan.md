# Kafka 관측 도입안 — 승인 대기

현재는 검토 문서입니다. exporter 이미지 다운로드·컨테이너 기동·소비자 중단은 하지 않았습니다.

## 승인할 안

| 항목 | 제안 |
| --- | --- |
| 이미지 | `danielqsj/kafka-exporter:v1.11.0` (승인 후 digest 고정) |
| 위치 | 각 환경 compose 네트워크에서 `kafka:9092` 접속 |
| 자원 | 환경당 메모리 상한 128MiB, CPU 상한 0.2; 부하 후 조정 |
| 공개 범위 | dev `127.0.0.1:9308`, prod `127.0.0.1:9309`; 외부 공개 없음 |
| 수집 | 기존 host Alloy, 30초 간격, `env` 구분 |
| 접근 | Docker 소켓·호스트 파일·DB 접근 없음, Kafka 메타데이터·offset 조회 |

서버 전체 메모리 여유를 먼저 확인합니다. 두 환경이면 exporter 상한도 최대 256MiB 추가됩니다.
현재 Kafka는 애플리케이션과 같은 신뢰 네트워크를 사용합니다. ACL을 쓸 때는 대상 토픽/그룹의 Describe·Read에 필요한 최소 권한을 공식 Kafka 권한표와 대조합니다. 쓰기·그룹 오프셋 변경 권한은 주지 않습니다.
Kafka 4.2.2와 exporter의 프로토콜 버전 호환은 dev에서 확인해야 하며 기본값이 맞다고 가정하지 않습니다.

## 수집과 화면

- `kafka_brokers`: 브로커 발견 수. exporter UP만으로 브로커 정상이라 판단하지 않습니다.
- `kafka_consumergroup_lag`: 그룹·토픽·파티션별 미처리 건수. 음수/미초기화 offset은 정상 0으로 치환하지 않습니다.
- `kafka_topic_partition_current_offset` 증가량: 토픽의 offset 증가 추정치. 실제 업무 성공 수나 정확한 메시지 유입량으로 표시하지 않습니다.
- 화면 첫 줄은 브로커 상태·미처리 그룹·증가 추이만 표시하고 파티션 상세는 접습니다.
- 그룹 합계·소비 offset 변화·lag 증가를 함께 보고, 도메인별 허용 대기시간을 정한 뒤 호출 기준을 확정합니다. 1건 지연이나 단순 증가만으로 호출하지 않습니다.
- exporter 부재·브로커 조회 실패·미배포와 lag=0을 구분합니다. 운영 중지된 환경은 #772 의도 목록을 따릅니다.

## 승인 후 완료 순서

1. compose profile로 exporter를 추가하고 이미지 digest·자원 상한·loopback 바인딩을 확인합니다.
2. Alloy 수집·환경별 운영 중지 시 기대 목록을 연동합니다. 앱 서비스 enum에 exporter를 넣어 앱 장애로 취급하지 않습니다.
3. dev의 대상 그룹 담당자와 이벤트·허용 지연·중단 시간을 정합니다.
4. 실제 소비자별 키를 확인합니다. 예: `kidly.product.kafka.consumer.enabled`, `kidly.settlement.kafka.consumer.enabled`, 결제의 별도 `kidly.payment.kafka.settlement-consumer.enabled`.
5. 승인된 소비자만 제어해 lag 상승·알림·재개 후 해소를 확인합니다. 토픽·오프셋·DLT를 임의로 바꾸지 않습니다.
6. 아웃박스·DLT 건수/최고 대기시간은 각 도메인 담당이 구현합니다. 정산 #730을 중복 구현하지 않습니다.

근거: [공식 v1.11.0 릴리스](https://github.com/danielqsj/kafka_exporter/releases/tag/v1.11.0), [공식 지표·옵션](https://github.com/danielqsj/kafka_exporter#readme).
