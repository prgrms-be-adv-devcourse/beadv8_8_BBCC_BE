# 판매자 도메인

## Kafka 발행 (`app/SellerEventRelay`, #548)
- 판매자가 발행한 이벤트(`seller.*`)를 커밋 뒤 토픽 `kidly.seller.events`로도 보낸다. 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(판매자 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽은 각 도메인이 Kafka로 옮긴다(받는 도메인의 일). 회원은 Kafka로 받는다(#608)
- 전송은 판매자 전용 스레드 하나(`seller-kafka-sender`)에서 한다. 브로커가 죽어 `send`가 `max.block.ms`만큼 멈춰도 커밋 뒤 요청 스레드는 기다리지 않는다
  - 스레드가 하나라 받은 순서대로 보낸다. Relay는 `HIGHEST_PRECEDENCE`로 다른 커밋 뒤 리스너보다 먼저 대기열에 넣는다
  - 대기열은 10,000건까지다. 넘치면 버리고 ERROR 로그를 남긴다. 앱이 내려갈 때는 남은 전송을 10초까지 기다린다
- 보내지 못하면 예외 없이 본문 없는 ERROR 로그만 남긴다. Outbox가 없어 대기열에 있는 동안 앱이 죽거나 브로커가 꺼져 있으면 Kafka 쪽 메시지는 잃는다
- 설정은 판매자 안(`out.kafka.SellerKafkaConfig`)에만 둔다: 토픽 선언(파티션 3 · 복제 1, 파티션 수는 바꾸지 않는다), 판매자 전용 템플릿(`max.block.ms` 3초, 본문을 찍는 기본 실패 로그 끔), 전송 스레드

## 다시 발행 (`app/SellerChangedRepublishService`, #643)
- `POST /api/v1/admin/sellers/changed-events`(관리자): 판매자마다(활성 · 정지 모두) 지금 상태로 `seller.changed`를 다시 보내고 보낸 수를 돌려준다. 판매자는 바꾸지 않는다
- 이벤트를 놓친 쪽(새로 붙은 검색 색인, 색인 재구축)을 따라잡게 할 때 쓴다. 받는 쪽은 같은 값으로 덮어써 여러 번 불러도 결과가 같다. 회원은 SELLER 역할을 잃을 때만 토큰을 무효화하므로 상태가 그대로인 이벤트로는 무효화되지 않는다
- 위 Kafka 발행과 같은 길로 나가므로 대기열(10,000건)을 넘는 판매자 수면 넘친 만큼 버려진다

## DLT 보관 · 재처리 (`app/SellerDeadLetterService`, #962)
- `in/SellerDeadLetterKafkaListener`가 `kidly.member.events.seller.dlt`를 그룹 `kidly.seller.dlt`(earliest)로 읽어 `seller.dead_letter`(`domain/SellerDeadLetter`)에 DLT 위치당 1행으로 보관한다. 같은 위치가 이미 있으면 건너뛴다(DLT는 파티션 1개를 컨테이너 하나가 차례로 읽고, 유니크 제약이 뒷받침한다). 저장 실패는 최대 60초 간격으로 끝없이 재시도하고, DLT의 DLT는 없다
- `/api/v1/admin/seller-dead-letters`(관리자): 목록(status 기본 OPEN) · 상세 · `POST /{id}/reprocess`. 응답과 로그에 본문 · 예외 메시지를 넣지 않는다
- 재처리는 원래 토픽에 다시 보내지 않고 `MemberEventListener.on`을 직접 부른다(원래 토픽은 다른 도메인도 받는다). 행을 비관적 쓰기 잠금으로 잡은 한 트랜잭션에서 처리기를 돌리고 REPROCESSED로 바꾸므로 동시에 눌러도 처리기는 한 번만 돈다. 처리기가 실패하면 그 트랜잭션은 롤백되고, 별도 트랜잭션에서 횟수 · 예외 종류만 남겨 200/OPEN을 돌려준다
