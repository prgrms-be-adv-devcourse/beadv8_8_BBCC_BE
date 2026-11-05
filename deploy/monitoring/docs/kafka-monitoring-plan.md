# Kafka 수집 연결

사용자가 이미지 도입·dev 검증을 승인했고, 현재는 **수집 연결부터 검토**하기로 결정했습니다.
실제 소비자 중단·합성 업무 이벤트 발행·lag 상승 시험은 이번 검토에서 진행하지 않습니다. #774 전체 완료를 뜻하지 않습니다.

## 구현한 구성

| 항목 | 내용 |
| --- | --- |
| 이미지 | `danielqsj/kafka-exporter:v1.11.0`, OCI digest 고정 |
| 접속 | 환경별 compose 네트워크의 `kafka:9092` |
| 자원 | 환경당 메모리 128MiB·CPU 0.2 상한 |
| 포트 | dev `127.0.0.1:9308`, prod `127.0.0.1:9309` |
| 권한 | 비루트·읽기 전용·capability 제거. Docker 소켓·앱 비밀값 전달 없음 |
| 수집 | 기존 Alloy, 30초 간격. 브로커·lag·offset·up만 전달 |
| 운영 의도 | 앱과 별도 기대 목록. 미도입·의도적 중지 환경을 구분 |

클라이언트 프로토콜은 4.0.0으로 고정했습니다. Kafka 브로커의 버전을 변경한 것은 아닙니다.
상한은 예약 메모리가 아니며, 두 환경 동시 운영 시 전체 여유는 별도로 측정해야 합니다.

## 확인한 것과 남은 것

- 기존 dev Kafka 4.2.2 네트워크의 임시 컨테이너에서 broker=1, lag 시계열 87개, 토픽 파티션 offset 56개를 읽었습니다.
- 당시 exporter 메모리는 약 5.6MiB/128MiB였습니다. 시험 컨테이너는 자동 정리했습니다.
- 실제 쿼리 문법과 운영 중지·환경 분리·음수 offset·증가 이력 조건을 합성 지표로 검사했습니다.
- 상시 compose 운영과 Prometheus Targets→대시보드까지의 최종 수집 경로는 추가 dev 확인이 필요합니다. 직접 exporter 조회를 이 종단 검증 완료로 간주하지 않습니다.

## 알림·화면의 의미

- exporter UP만으로 브로커·업무 정상이라고 판단하지 않습니다.
- 음수/미초기화 offset은 정상 0으로 바꾸지 않습니다. 모든 파티션 offset이 확인된 그룹·토픽만 합산합니다.
- 토픽 offset 증가는 추정치이며 정확한 유입량·업무 성공 수가 아닙니다.
- **lag 증가 호출은 기본 일시정지**입니다. 도메인별 허용 대기시간을 확정하기 전 활성화하지 않습니다.
- 새 유입 없이 lag가 일정하게 남는 정체는 증가 규칙만으로 감지하지 못합니다. 별도 잔류 조건도 필요합니다.

## 후속 검증

1. 수집 연결 검토·종단 확인을 마친 뒤 도메인 담당자와 소비자·합성 이벤트·시험 시간을 합의합니다.
2. 실제 제어 키를 확인합니다. 예: `kidly.product.kafka.consumer.enabled`, `kidly.settlement.kafka.consumer.enabled`, 결제의 별도 `kidly.payment.kafka.settlement-consumer.enabled`.
3. 승인된 소비자만 제어해 lag 상승·알림·재개 후 해소를 확인합니다. 토픽·offset·DLT·업무 데이터를 임의로 바꾸지 않습니다.
4. 아웃박스·DLT 지표는 각 도메인 담당이 구현하며 정산 #730을 중복 구현하지 않습니다.

근거: [공식 v1.11.0](https://github.com/danielqsj/kafka_exporter/releases/tag/v1.11.0), [공식 지표·옵션](https://github.com/danielqsj/kafka_exporter#readme).
