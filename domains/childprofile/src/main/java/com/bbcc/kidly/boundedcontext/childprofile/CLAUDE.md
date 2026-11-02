# 자녀 프로필 도메인

## Kafka 발행 (아웃박스, #548 · #562)
- 자녀 프로필이 발행한 이벤트(`child.changed`)를 토픽 `kidly.childprofile.events`로 보낸다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(자녀 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽(주문 · 추천)은 모두 Kafka로 받는다. 주문은 `kidly.order.kafka.consumer.enabled=false`일 때만 Spring 이벤트(`@DomainEventListener`)로 되돌아간다
- 저장(`app/outbox/ChildProfileOutboxRecorder`): 커밋 직전(`BEFORE_COMMIT`)에 자녀 프로필이 발행한 이벤트만 `childprofile.child_profile_outbox`에 저장한다
  - 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 커밋되지 않는다. 같은 `eventId`는 한 번만 저장한다(받는 쪽 테스트가 중복 수신을 보려고 같은 이벤트를 두 번 발행한다)
  - Kafka로 회원 탈퇴(`member.withdrawn`)를 받아 자녀를 지우는 리스너(#569)가 자기 트랜잭션에서 발행한 `child.changed`도 그 트랜잭션 안에서 저장한다
  - 트랜잭션 밖에서 발행하거나, 다른 커밋 직전 리스너 · 커밋 때의 flush 중에 발행한 이벤트는 저장되지 않는다
  - Kafka 보내기를 꺼도(`kidly.kafka.producer.enabled=false`) 저장은 한다
- 릴레이(`in/outbox/ChildProfileOutboxScheduler` → `app/outbox/ChildProfileOutboxRelayService`): 1초마다 보내지 않은 메시지를 `id` 순서로 최대 100건 보낸다
  - 브로커 응답을 기다리는 동기 전송이다. 실패하면 `attempt_count` · `last_error`를 남기고 그 자리에서 멈춘 뒤 다음 주기에 같은 메시지부터 다시 보낸다
  - 한 트랜잭션에서 PostgreSQL 트랜잭션 advisory lock을 먼저 얻는다. 못 얻으면 아무것도 하지 않는다. 서버가 여러 대여도 릴레이는 한 번에 하나다(팀 Kafka 규칙의 순서 항목)
  - 보내기와 결과 기록이 한 트랜잭션이라, 브로커가 멈추면 실패한 메시지 하나를 기다리는 동안(최대 약 15초) DB 연결 하나를 쥔다
  - 보낸 뒤 커밋 전에 앱이 멈추거나 커밋이 실패하면 이번 실행에서 보낸 메시지(최대 100건)가 다시 간다. `id`는 저장 순서라 동시 트랜잭션의 커밋 순서와 다를 수 있다. 받는 쪽은 `occurredAt` 비교로 중복 · 역순을 거른다
  - `kidly.kafka.producer.enabled`가 false면 릴레이 서비스와 스케줄러를 만들지 않는다
- 정리: 보낸 지 7일(브로커 보존 기간) 지난 행을 매일 04:00에 지운다. 보내지 않은 행은 지우지 않는다. 릴레이와 같은 스케줄러가 부르므로 Kafka 보내기를 끄면 정리도 돌지 않는다(보낸 행도 생기지 않는다)
- 본문에 아이의 키 · 몸무게 · 생년월일 · 성별이 있으므로 로그에 남기지 않는다. 실패 로그는 `eventType` · `eventId` · 키 · 원인만, 스케줄러 예외는 예외 종류만 남긴다
- 설정은 자녀 프로필 안에만 둔다: `out/outbox/ChildProfileKafkaConfig`(토픽 선언 파티션 3 · 복제 1, 전용 템플릿 `max.block.ms` 3초 · `delivery.timeout.ms` 10초, 본문을 찍는 기본 실패 로그 끔), `childprofile.yml`(`kidly.childprofile.outbox.*`)

## 자녀 프로필 서비스 (`apps/childprofile-service`, #682)
- 이 도메인은 이 서비스에서만 돈다. kidly-app에는 없다. 자녀 API · 자녀 이벤트 발행 · 회원 탈퇴 수신(구독 그룹 `kidly.childprofile`) 모두 서비스가 한다
- 테스트는 `apps/childprofile-service/src/test`에 있다. 회원 도메인이 없으므로 회원 탈퇴 수신 테스트는 회원이 보내는 형식으로 토픽에 직접 넣는다
- Gateway가 `/api/v1/children/**`를 `KIDLY_CHILDPROFILE_URI`로 보낸다. 기본값이 서비스라 서비스가 없으면 자녀 API가 실패한다
- 실행: `./gradlew :apps:childprofile-service:bootRun`(포트 8084, 프로필 local, 초기 자녀 데이터를 넣는다). 서버는 compose profile `childprofile`, dev `127.0.0.1:8085` · prod `127.0.0.1:8088`, 스프링 프로필은 서버 `.env`의 `SPRING_PROFILES_ACTIVE`(dev는 `prod,dev`라 초기 자녀 데이터를 넣고, prod는 넣지 않는다)
- Flyway: 자기 이력 테이블 `childprofile.flyway_schema_history_childprofile`. 자녀 테이블이 있는 DB는 첫 기동 때 `202610300953`으로 baseline
  - 새 마이그레이션은 이 모듈의 `db/migration/childprofile/`에 둔다. kidly-app에는 이 jar가 없어 서비스만 적용한다
  - 빈 DB에서는 kidly-app이 먼저 마이그레이션해야 한다. kidly-app의 init(고칠 수 없음)이 자녀 테이블도 만들어, 서비스가 먼저 만들면 kidly-app의 init이 `relation "child_profile" already exists`로 실패한다(로컬에서 확인). 서버 compose는 서비스가 `app`의 healthy를 기다린다
  - kidly-app이 먼저 만든 빈 DB에는 아웃박스가 없다. 서비스가 baseline으로 원래 아웃박스 파일을 건너뛰므로 `V202611021428__child_profile_outbox_if_missing`이 같은 테이블을 만든다
