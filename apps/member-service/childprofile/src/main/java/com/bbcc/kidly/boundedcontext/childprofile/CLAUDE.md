# 자녀 프로필 도메인

## 자녀 계정 연결 (#837)
- `kidly.auth.events`의 `child-account.changed`를 받아 계정 ID별 프로필 연결을 복사한다. 삭제 행을 남겨 중복 · 역순 수신에도 연결이 되살아나지 않는다
- `ChildAccountLinkService`가 `ChildAccountLink`를 저장 · 삭제 표시하며 `ChildAccountLinkRepository`가 연결을 조회한다
- 자녀 본인 조회는 연결의 프로필 ID와 토큰의 보호자 ID로 기존 부모 상세 조회를 사용한다. 배포 전 계정은 연결이 없으며 계정을 다시 만들면 연결된다
- 리스너는 앱 기본 시작 위치 `latest`를 쓴다. 토픽별 DLT는 `kidly.auth.events.childprofile.dlt`와 `kidly.member.events.childprofile.dlt`다

## 자녀 등록과 안내
- 등록 시 보호자 동의를 기록한다. 체형과 선호 핏에는 선택 동의가 필요하다 (#849)
- 자녀 등록 전 보호자 본인인증이 필요하다. 탈퇴하면 인증 기록을 삭제한다 (#866)
- 회원 정지 시 자녀 프로필을 모두 삭제한다 (#886)
- 자녀는 첫 로그인 안내 확인 여부를 조회하고 확인 시각을 기록한다 (#889)

## Kafka 발행 (아웃박스, #548 · #562)
- 자녀 프로필이 발행한 이벤트(`child.changed`)를 토픽 `kidly.childprofile.events`로 보낸다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(자녀 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽(인증 · 주문 · 추천)은 모두 Kafka로 받는다
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

## 회원 서비스의 자녀 프로필 모듈 (#809)
- 자녀 API, 자녀 이벤트 발행과 회원 탈퇴 수신은 회원 서비스에서 실행한다. 도메인 간 통신은 Kafka를 쓴다.
- 앱 통합 · 기능 테스트는 `apps/member-service/src/test`, 단위 · 계층 테스트는 `apps/member-service/childprofile/src/test`에 있다.
- 실행: `cd apps/member-service && ./gradlew bootRun` (포트 8081, local).
- Flyway는 `childprofile.flyway_schema_history_childprofile` 이력을 유지하며 회원 이력과 별도로 실행한다. 파일은 이 모듈의 `db/childprofile-migration`에 둔다. 회원 Flyway가 읽는 `db/migration`과 init 버전(V202610160256)이 겹쳐 위치를 나눴다.
