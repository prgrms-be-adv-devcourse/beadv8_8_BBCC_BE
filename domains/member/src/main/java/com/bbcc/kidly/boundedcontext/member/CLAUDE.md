# 회원 도메인

## Kafka 발행 (#548 · #562)
- 회원이 발행한 이벤트(`member.*`)를 토픽 `kidly.member.events`로 보낸다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(회원 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽: 알림(`member.registered`) · 판매자(`member.changed`) · 자녀 프로필(`member.withdrawn`)은 Kafka로(#567 · #568 · #569), 결제(`member.registered`)는 아직 Spring 이벤트(`@DomainEventListener`)로 받는다. Kafka로 옮기는 건 받는 도메인이 한다. 회원이 자기 이벤트(`member.withdrawn`)를 받는 리스너는 같은 도메인 안이라 Spring으로 둔다
- 보내는 길이 둘이다. 잃으면 복구할 방법이 없는 탈퇴만 아웃박스로 보낸다(결정: 잃으면 자녀 프로필이 탈퇴 회원의 자녀 정보를 지우지 못함)

| 이벤트 | 보내는 길 |
|---|---|
| `member.registered` · `member.changed` | 커밋 뒤 바로(`app/MemberEventRelay` → 회원 전용 스레드 `member-kafka-sender`) |
| `member.withdrawn` | 아웃박스(`app/outbox/MemberOutboxRecorder` → `member.member_outbox` → 1초 릴레이) |

- 커밋 뒤 바로(가입 · 변경)
  - Relay는 `HIGHEST_PRECEDENCE`로 다른 커밋 뒤 리스너보다 먼저 대기열에 넣는다. 스레드가 하나라 받은 순서대로 보낸다. 브로커가 죽어도 커밋 뒤 요청 스레드는 기다리지 않는다
  - 대기열은 10,000건까지다. 넘치면 버리고 ERROR 로그를 남긴다. 앱이 내려갈 때는 남은 전송을 10초까지 기다린다. 대기열에 있는 동안 앱이 죽거나 브로커가 꺼져 있으면 잃는다
  - 설정: `out/kafka/MemberKafkaConfig`(토픽 선언 파티션 3 · 복제 1, 전용 템플릿 `max.block.ms` 3초, 전송 스레드)
- 아웃박스(탈퇴)
  - 커밋 직전(`BEFORE_COMMIT`)에 탈퇴 이벤트만 저장한다. 업무가 롤백되면 메시지도 없고, 저장이 실패하면 탈퇴도 커밋되지 않는다. 트랜잭션 밖에서 발행하면 저장되지 않는다. Kafka 보내기를 꺼도 저장은 한다
  - 릴레이(`in/outbox/MemberOutboxScheduler` → `app/outbox/MemberOutboxRelayService`): 1초마다 보내지 않은 메시지를 `id` 순서로 최대 100건, 브로커 응답을 기다리며 보낸다. 실패하면 `attempt_count` · `last_error`를 남기고 그 자리에서 멈춘 뒤 다음 주기에 같은 메시지부터 다시 보낸다
  - 한 트랜잭션에서 PostgreSQL 트랜잭션 advisory lock을 먼저 얻는다. 못 얻으면 아무것도 하지 않는다(서버가 여러 대여도 릴레이는 한 번에 하나). 브로커가 멈추면 실패한 메시지 하나를 기다리는 동안(최대 약 15초) DB 연결 하나를 쥔다
  - 커밋 전에 앱이 멈추거나 커밋이 실패하면 그 실행에서 보낸 메시지(최대 100건)가 다시 간다. 받는 쪽(자녀 프로필)은 이미 지운 자녀를 다시 찾지 않아 두 번 받아도 결과가 같다
  - 탈퇴는 가입 · 변경보다 늦게 나갈 수 있다(릴레이 주기 1초, 실패하면 다시 보낼 때까지, 스케줄러 스레드를 다른 도메인과 함께 씀). 그래서 같은 회원 키라도 가입 · 변경과 순서가 보장되지 않는다(팀 규칙의 순서 항목에서 벗어난 부분). `member.withdrawn`을 받는 곳은 자녀 프로필뿐이고 자녀 프로필은 가입 · 변경을 받지 않으므로 영향이 없다
  - 정리: 보낸 지 7일(브로커 보존 기간) 지난 행을 매일 04:00에 지운다. 릴레이와 같은 스케줄러라 Kafka 보내기를 끄면 정리도 돌지 않는다
  - `kidly.kafka.producer.enabled`가 false면 릴레이 서비스와 스케줄러를 만들지 않는다
  - 설정: `out/outbox/MemberOutboxKafkaConfig`(전용 템플릿 `max.block.ms` 3초 · `delivery.timeout.ms` 10초), `member.yml`(`kidly.member.outbox.*`)
- `member.registered`에 이메일이 있으므로 본문을 로그에 남기지 않는다. 실패 로그 · 기록은 `eventType` · `eventId` · 키 · 원인만, 스케줄러 예외는 예외 종류만 남긴다. 전용 템플릿 둘 다 본문을 찍는 기본 실패 로그를 끈다
