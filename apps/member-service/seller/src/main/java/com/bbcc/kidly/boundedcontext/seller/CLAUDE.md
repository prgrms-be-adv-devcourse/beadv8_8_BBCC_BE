# 판매자 도메인

## Kafka 발행 (`app/outbox/SellerOutboxRecorder` #1382)
- 승인 · 정지 · 탈퇴와 상호명 수정 시 `seller.changed`를 발행한다. 같은 상호명으로 수정하면 발행하지 않는다
- 판매자가 발행한 이벤트(`seller.*`)를 토픽 `kidly.seller.events`로 보낸다. 롤백되거나 트랜잭션 밖에서 발행하면 보내지 않는다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(`seller.changed`는 판매자 ID, `seller.application-rejected`는 신청 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON, support `outbox.EventEnvelope`). `__TypeId__`는 붙이지 않는다
- 받는 쪽은 각 도메인이 Kafka로 옮긴다(받는 도메인의 일). 회원은 Kafka로 받는다(#608)
- 모든 판매자 이벤트를 아웃박스로 보낸다.

| 이벤트 | 보내는 길 |
|---|---|
| `seller.changed` · `seller.application-rejected` | 아웃박스 저장 → 커밋 뒤 전송 |

- 아웃박스
  - 커밋 직전(`BEFORE_COMMIT`)에 저장한다. 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 커밋되지 않는다. 트랜잭션 밖에서 발행하면 저장되지 않는다. Kafka 보내기를 꺼도 저장은 한다
  - 커밋 뒤 대기열에서 바로 전송한다. 릴레이는 1분마다 5분 지난 미전송 행을 최대 100건 보낸다. 릴레이 실패 시 시도와 오류를 기록하고 멈춘다
  - 한 트랜잭션에서 PostgreSQL 트랜잭션 advisory lock을 먼저 얻는다. 못 얻으면 아무것도 하지 않는다(서버가 여러 대여도 릴레이는 한 번에 하나). 브로커가 멈추면 실패한 메시지 하나를 기다리는 동안(최대 약 15초) DB 연결 하나를 쥔다
  - 커밋 전에 앱이 멈추거나 커밋이 실패하면 그 실행에서 보낸 메시지(최대 100건)가 다시 간다. 받는 쪽은 두 번 받아도 결과가 같다(회원은 `occurredAt`으로 늦은 변경만 반영, 복사본은 같은 값으로 덮어씀, 알림은 같은 메시지로 메일을 한 통만 만듦)
  - 정리: 보낸 지 7일(브로커 보존 기간) 지난 행을 매일 04:00에 지운다. 릴레이와 같은 스케줄러라 Kafka 보내기를 끄면 정리도 돌지 않는다
  - `kidly.kafka.producer.enabled`가 false면 릴레이 서비스와 스케줄러를 만들지 않는다
- 설정은 판매자 안에만 둔다: 토픽 선언과 전용 템플릿(`out/outbox/SellerOutboxKafkaConfig`, 파티션 3 · 복제 1, `max.block.ms` 3초 · `delivery.timeout.ms` 10초, 본문을 찍는 기본 실패 로그 끔), `seller.yml`(`kidly.seller.outbox.*`)

## 다시 발행 (`app/SellerChangedRepublishService`, #643)
- `POST /api/v1/admin/sellers/changed-events`(관리자): 판매자마다(활성 · 정지 모두) 지금 상태로 `seller.changed`를 다시 보내고 보낸 수를 돌려준다. 판매자는 바꾸지 않는다
- 이벤트를 놓친 쪽(새로 붙은 검색 색인, 색인 재구축)을 따라잡게 할 때 쓴다. 받는 쪽은 같은 값으로 덮어써 여러 번 불러도 결과가 같다. 회원은 SELLER 역할을 잃을 때만 토큰을 무효화하므로 상태가 그대로인 이벤트로는 무효화되지 않는다
- 위 아웃박스로 나가므로 한 트랜잭션에 판매자 수만큼 행을 저장한다. 대기열이 가득 차면 남은 행은 릴레이가 보낸다

## DLT 보관 · 재처리 (`app/SellerDeadLetterService`, #962)
- `in/SellerDeadLetterKafkaListener`가 `kidly.member.events.seller.dlt`를 그룹 `kidly.seller.dlt`(earliest)로 읽어 `seller.dead_letter`(`domain/SellerDeadLetter`)에 DLT 위치당 1행으로 보관한다. 같은 위치가 이미 있으면 건너뛴다(DLT는 파티션 1개를 컨테이너 하나가 차례로 읽고, 유니크 제약이 뒷받침한다). 저장 실패는 최대 60초 간격으로 끝없이 재시도하고, DLT의 DLT는 없다
- `/api/v1/admin/seller-dead-letters`(관리자): 목록(status 기본 OPEN) · 상세 · `POST /{id}/reprocess`. 응답과 로그에 본문 · 예외 메시지를 넣지 않는다
- 재처리는 원래 토픽에 다시 보내지 않고 `MemberEventListener.on`을 직접 부른다(원래 토픽은 다른 도메인도 받는다). 행을 비관적 쓰기 잠금으로 잡은 한 트랜잭션에서 처리기를 돌리고 REPROCESSED로 바꾸므로 동시에 눌러도 처리기는 한 번만 돈다. 처리기가 실패하면 그 트랜잭션은 롤백되고, 별도 트랜잭션에서 횟수 · 예외 종류만 남겨 200/OPEN을 돌려준다
