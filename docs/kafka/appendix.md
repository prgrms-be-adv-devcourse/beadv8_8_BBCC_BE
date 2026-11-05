# 팀 합의 후 반영한 것

| 항목 | 반영 내용 |
|---|---|
| 토픽 | 보내는 도메인마다 하나(`kidly.{보내는도메인}.events`)입니다. 재고 이벤트는 `kidly.product.events`, 구매확정은 `kidly.order.events`, 자녀 변경은 `kidly.childprofile.events`에 들어갑니다 |
| 읽기 실패 | 모르는 enum 값처럼 읽지 못한 메시지는 재시도 없이 DLT로 보내고 Slack으로 알립니다 |
| 읽기 시작 위치 | `auto-offset-reset: latest`입니다. 첫 배포 때 오프셋을 맞추는 명령을 쓰지 않습니다 |
| `max.block.ms` | 앱은 기본값(60초)입니다. 문제가 생기면 그때 바꿉니다 |
| 오프셋 보존 기간 | 기본값(7일)입니다 |
| 재시도 · 트랜잭션 | 재시도 정책과 리스너의 `@Transactional`은 각 도메인이 정합니다 |
| 테스트 | Kafka 컨테이너는 `@KafkaIntegrationTest`에서만 띄우고, 나머지 테스트는 보내기 스위치로 Kafka 보내기를 끕니다. 결과는 `support.AsyncWait`로 기다립니다 |
| ArchUnit | 모든 받는 쪽을 옮긴 뒤 마무리 단계에 넣습니다 |
| DLT 목적지 | DLT 전송기의 기본 목적지(`{토픽}-dlt`)를 쓰지 않고 `{토픽}.{받는도메인}.dlt`, 파티션 `-1`로 지정합니다 |
| 전송 실패 로그 | 앱은 Boot 기본 템플릿의 실패를 토픽 · 키 · `eventType` · `eventId`로 남기고 본문은 빼며, 직접 만든 템플릿은 기본 실패 로그를 끄고 실패를 직접 남깁니다 |
