# 회원 도메인

## Kafka 발행 (`app/MemberEventRelay`, #548)
- 회원이 발행한 이벤트(`member.*`)를 커밋 뒤 토픽 `kidly.member.events`로도 보낸다. 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(회원 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽(결제 · 알림 · 판매자 · 자녀 프로필)은 아직 Spring 이벤트(`@DomainEventListener`)로 받는다. Kafka로 옮기는 건 받는 도메인이 한다. 회원이 자기 이벤트(`member.withdrawn`)를 받는 리스너는 같은 도메인 안이라 Spring으로 둔다
- 전송은 회원 전용 스레드 하나(`member-kafka-sender`)에서 한다. 브로커가 죽어 `send`가 `max.block.ms`만큼 멈춰도 커밋 뒤 요청 스레드는 기다리지 않는다
  - 스레드가 하나라 받은 순서대로 보낸다. Relay는 `HIGHEST_PRECEDENCE`로 다른 커밋 뒤 리스너보다 먼저 대기열에 넣는다
  - 대기열은 10,000건까지다. 넘치면 버리고 ERROR 로그를 남긴다. 앱이 내려갈 때는 남은 전송을 10초까지 기다린다
- 보내지 못하면 예외 없이 본문 없는 ERROR 로그만 남긴다. `member.registered`에 이메일이 있으므로 본문을 로그에 남기지 않는다. Outbox가 없어 대기열에 있는 동안 앱이 죽거나 브로커가 꺼져 있으면 Kafka 쪽 메시지는 잃는다
  - `member.registered`는 결제가 지갑을 만드는 이벤트다. 결제가 이 이벤트를 Kafka로 받기 전에 Outbox 여부를 다시 정한다
- 설정은 회원 안(`out.kafka.MemberKafkaConfig`)에만 둔다: 토픽 선언(파티션 3 · 복제 1, 파티션 수는 바꾸지 않는다), 회원 전용 템플릿(`max.block.ms` 3초, 본문을 찍는 기본 실패 로그 끔), 전송 스레드
