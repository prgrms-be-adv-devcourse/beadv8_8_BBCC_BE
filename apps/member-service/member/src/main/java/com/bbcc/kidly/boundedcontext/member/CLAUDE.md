# 회원 도메인

## Kafka 발행 (#548 · #562 · #588)
- 회원이 발행한 이벤트(`member.*`)를 토픽 `kidly.member.events`로 보낸다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(회원 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽은 모두 Kafka로 받는다
  - 알림 `member.registered`, 판매자 `member.changed`, 결제 `member.registered` · `member.withdrawn`
  - 자녀 프로필 · 인증 · 회원 자신 `member.withdrawn`. 회원은 배송지를 지우는 리스너(`in/ShippingAddressMemberEventListener`, 구독 그룹 `kidly.member`)로 받는다
  - 회원 자신도 Kafka로 받는 이유: 회원 서비스 안의 수신자도 다른 서비스와 같은 길로 받게 해 수신 경로를 하나로 둔다(팀 규칙 '자기 이벤트는 Spring'의 예외)
- 보내는 길이 둘이다. 잃으면 피해가 영구적인 탈퇴만 아웃박스로 보낸다(결정: 잃으면 자녀 프로필이 탈퇴 회원의 자녀 정보를 지우지 못함)

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
  - 커밋 전에 앱이 멈추거나 커밋이 실패하면 그 실행에서 보낸 메시지(최대 100건)가 다시 간다. 받는 쪽(자녀 프로필 · 인증 · 회원 배송지)은 이미 지운 행을 다시 찾지 않아 두 번 받아도 결과가 같다
  - 탈퇴는 가입 · 변경보다 늦게 나갈 수 있다(릴레이 주기 1초, 실패하면 다시 보낼 때까지, 스케줄러 스레드를 다른 도메인과 함께 씀). 그래서 같은 회원 키라도 가입 · 변경과 순서가 보장되지 않는다(팀 규칙의 순서 항목에서 벗어난 부분). `member.withdrawn`을 받는 곳(자녀 프로필 · 인증 · 회원 배송지)은 가입 · 변경을 받지 않으므로 영향이 없다
  - 정리: 보낸 지 7일(브로커 보존 기간) 지난 행을 매일 04:00에 지운다. 릴레이와 같은 스케줄러라 Kafka 보내기를 끄면 정리도 돌지 않는다
  - `kidly.kafka.producer.enabled`가 false면 릴레이 서비스와 스케줄러를 만들지 않는다
  - 설정: `out/outbox/MemberOutboxKafkaConfig`(전용 템플릿 `max.block.ms` 3초 · `delivery.timeout.ms` 10초), `member.yml`(`kidly.member.outbox.*`)
- `member.registered`에 이메일이 있으므로 본문을 로그에 남기지 않는다. 실패 로그 · 기록은 `eventType` · `eventId` · 키 · 원인만, 스케줄러 예외는 예외 종류만 남긴다. 전용 템플릿 둘 다 본문을 찍는 기본 실패 로그를 끈다

## Access Token 무효화 (#686)
- 회원은 Access Token을 직접 발급 · 무효화하지 않는다. 탈퇴 · 관리자 정지 · 판매자 정지에서 `shared.auth.out.MemberTokenRevoker.revoke(memberId, revokedAt)`만 부른다(호출자 트랜잭션 안에서만, `MemberService` · `AdminMemberService` · `SellerStateService`)
- 구현은 인증 도메인(`AccessTokenRevocationService`)이다. `auth.tokens-revoked`를 `kidly.auth.events`로 커밋 뒤 바로 보내고, Gateway가 받아 그 시각까지 발급된 토큰을 거절한다. 잃으면 Gateway가 그 회원의 Access Token을 만료(최대 30분)까지 통과시키지만 그 뒤로는 피해가 없어 아웃박스를 쓰지 않는다(인증 도메인 CLAUDE.md)

## Kafka 수신 (`in/SellerEventListener`, #608)
- 구독 그룹 `kidly.member`로 `kidly.seller.events`의 `seller.changed`만 받는다. 그 밖의 종류는 읽지 않고 건너뛴다. 종류는 헤더 `eventType`으로 고른다
- 본문(공통 칸 7개 + `data`)을 `SellerChangedEvent`로 읽어 `SellerStateService.apply`에 넘긴다. 트랜잭션은 `apply`가 열고, SELLER를 잃으면 그 안에서 `MemberTokenRevoker`를 부른다(→ `auth.tokens-revoked`)
- 같은 메시지를 다시 받거나 순서가 바뀌어 와도 늦게 일어난 변경(`occurredAt`)만 반영하고, 이미 SELLER가 없으면 다시 무효화하지 않는다. 그래서 한 번 이상 받아도 된다
- 실패 처리(`in/MemberKafkaListenerConfig`): 한 건씩 처리 완료를 커밋하고, 실패하면 1초 간격으로 3번 더 시도한 뒤 DLT `kidly.seller.events.member.dlt`로 보낸다
  - 재시도 없이 바로 DLT: 읽지 못한 본문(`JacksonException`), 업무 규칙 위반(`BusinessException`. 회원 없음 등). 회원은 판매자보다 먼저 있으므로 다시 해도 같다
  - DLT 로그에는 `eventType` · `eventId` · 키 · 예외 종류만 남긴다(본문(상호명) · 예외 메시지는 남기지 않는다)
- 읽기 시작 위치는 앱 설정 `latest`(회의 10-29 안건 4). 구독 그룹이 처음 붙기 전 메시지는 읽지 않는다
- 보안 지연: 판매자 정지 → 무효화가 Kafka를 거쳐 비동기다. 그동안(보통 수 초, Kafka가 멈추면 그동안 내내) 정지된 판매자의 SELLER 토큰이 Gateway를 통과할 수 있다(MSA 가이드가 받아들인 한계)
- 판매자 토픽은 판매자가 선언한다. 회원은 DLT만 선언한다

## DLT 보관 · 재처리 (`app/MemberDeadLetterService`, #962)
- `in/MemberDeadLetterKafkaListener`가 `kidly.seller.events.member.dlt` · `kidly.member.events.member.dlt`를 그룹 `kidly.member.dlt`(earliest)로 읽어 `member.dead_letter`(`domain/MemberDeadLetter`)에 DLT 위치당 1행으로 보관한다. 같은 위치가 이미 있으면 건너뛴다(DLT는 파티션 1개를 컨테이너 하나가 차례로 읽고, 유니크 제약이 뒷받침한다). 저장 실패는 최대 60초 간격으로 끝없이 재시도하고, DLT의 DLT는 없다
- `/api/v1/admin/member-dead-letters`(관리자): 목록(status 기본 OPEN) · 상세 · `POST /{id}/reprocess`. 응답과 로그에 본문 · 예외 메시지를 넣지 않는다
- 재처리는 원래 토픽에 다시 보내지 않고 원래 토픽의 리스너(`SellerEventListener.on` · `ShippingAddressMemberEventListener.on`)를 직접 부른다(원래 토픽은 다른 도메인도 받는다). 행을 비관적 쓰기 잠금으로 잡은 한 트랜잭션에서 처리기를 돌리고 REPROCESSED로 바꾸므로 동시에 눌러도 처리기는 한 번만 돈다. 처리기가 실패하면 그 트랜잭션은 롤백되고, 별도 트랜잭션에서 횟수 · 예외 종류만 남겨 200/OPEN을 돌려준다
