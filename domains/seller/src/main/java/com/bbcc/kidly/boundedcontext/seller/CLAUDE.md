# 판매자 도메인

## Kafka 발행 (`app/SellerEventRelay`, #548)
- 판매자가 발행한 이벤트(`seller.*`)를 커밋 뒤 토픽 `kidly.seller.events`로도 보낸다. 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(판매자 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽(회원 · 주문 · 상품 · 추천)은 아직 Spring 이벤트(`@DomainEventListener`)로 받는다. Kafka로 옮기는 건 받는 도메인이 한다. 회원 ← `seller.changed`는 판매자 정지 때 토큰을 즉시 무효화하는 경로라 3단계 동안 Spring 이벤트로 둔다(4단계 Gateway 무효화와 함께 정한다)
- 전송은 판매자 전용 스레드 하나(`seller-kafka-sender`)에서 한다. 브로커가 죽어 `send`가 `max.block.ms`만큼 멈춰도 커밋 뒤 요청 스레드는 기다리지 않는다
  - 스레드가 하나라 받은 순서대로 보낸다. Relay는 `HIGHEST_PRECEDENCE`로 다른 커밋 뒤 리스너보다 먼저 대기열에 넣는다
  - 대기열은 10,000건까지다. 넘치면 버리고 ERROR 로그를 남긴다. 앱이 내려갈 때는 남은 전송을 10초까지 기다린다
- 보내지 못하면 예외 없이 본문 없는 ERROR 로그만 남긴다. Outbox가 없어 대기열에 있는 동안 앱이 죽거나 브로커가 꺼져 있으면 Kafka 쪽 메시지는 잃는다
- 설정은 판매자 안(`out.kafka.SellerKafkaConfig`)에만 둔다: 토픽 선언(파티션 3 · 복제 1, 파티션 수는 바꾸지 않는다), 판매자 전용 템플릿(`max.block.ms` 3초, 본문을 찍는 기본 실패 로그 끔), 전송 스레드
