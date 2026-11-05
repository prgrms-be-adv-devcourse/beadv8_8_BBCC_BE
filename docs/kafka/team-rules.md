# 팀 규칙 (필수)

| 항목 | 규칙 |
|---|---|
| 토픽 | `kidly.{보내는도메인}.events`. 보내는 도메인마다 하나이며, 그 도메인이 보내는 이벤트는 종류와 관계없이 모두 이 토픽에 넣습니다 |
| 표기 | 소문자만 씁니다. 단계는 `.`, 단어는 `-`로 구분합니다. `_`, 대문자, 환경 이름, 버전은 넣지 않습니다 |
| 파티션 · 복제 | 3 · 1. 만든 뒤에는 파티션 수를 바꾸지 않습니다. 늘리면 키가 들어가는 파티션이 바뀌어 순서가 깨집니다 |
| 토픽 생성 | 보내는 도메인이 `NewTopic` 빈으로 만듭니다. 받는 도메인은 자기 DLT만 만듭니다. 브로커의 토픽 자동 생성은 꺼져 있습니다 |
| 키 | `event.correlationId()` |
| 본문 | JSON 문자열. 공통 칸 7개(`eventId`, `eventType`, `schemaVersion`, `occurredAt`, `producer`, `aggregateId`, `correlationId`) + `data`. 이벤트 record를 그대로 JSON으로 바꾸면 5칸이 빠지므로(5칸은 필드가 아니라 `DomainEvent`의 기본 메서드) 보내는 쪽이 직접 채웁니다 |
| 헤더 | `eventType`, `eventId`(본문과 같은 값). Java 타입 헤더 `__TypeId__`는 쓰지 않습니다 |
| 값 | ID는 `Long`(JSON 숫자). 시각은 `LocalDateTime` ISO 문자열이며 한국 시간이고 시간대 표시를 붙이지 않습니다. `data`가 없는 메시지는 보내지 않습니다 |
| 이벤트 변경 | 필드는 **추가만** 합니다(삭제, 이름 변경, 타입 변경 금지). 개인정보 필드를 더 넣지 않습니다 |
| 모르는 필드 | 받는 쪽은 **모르는 필드를 무시합니다**(Jackson 3, spring-kafka 기본값이며 이 설정을 바꾸지 않습니다) |
| 새 enum 값 | **받는 쪽을 먼저 배포**한 뒤 보냅니다 |
| 읽기 실패 | 모르는 enum 값처럼 메시지를 읽지 못하면 null로 읽거나 건너뛰지 않습니다. **재시도 없이 DLT로 보내고 Slack으로 알립니다**(`READ_UNKNOWN_ENUM_VALUES_AS_NULL`을 켜지 않습니다) |
| 보내는 시점 | **DB 커밋이 확정된 뒤에만** 보냅니다. 방법은 도메인 자율입니다(4절) |
| 구독 그룹 | `kidly.{받는도메인}` |
| DLT | `{토픽}.{받는도메인}.dlt`, 파티션 1. 받는 도메인이 만듭니다. 처리는 사람이 확인한 뒤 그 받는 도메인이 DLT를 읽는 별도 consumer로 다시 합니다. **원래 토픽에 다시 넣지 않습니다.** "나중에 다시 하기"용 retry 토픽은 쓰지 않습니다. DLT 전송기(`DeadLetterPublishingRecoverer`)의 기본 목적지(`{토픽}-dlt`, 원래 파티션 번호)는 쓰지 않고, 목적지를 이 이름과 파티션 `-1`(Kafka가 고름)로 지정합니다(3절 리스너 설정 예시) |
| 자기 이벤트 | 자기 도메인 이벤트는 Kafka로 받지 않고 Spring 이벤트로 받습니다(member의 `member.withdrawn`, product의 `product.changed`) |
| 로그 | 메시지 본문을 로그와 예외 메시지에 넣지 않습니다. 남기는 것은 `eventType`, `eventId`, 키뿐입니다(본문의 개인정보가 Kafka와 DLT에 7일 남습니다) |

## 서비스의 공용 규칙

여러 도메인을 함께 실행하는 서비스에서도 아래 규칙을 지킵니다.

| 항목 | 규칙 |
|---|---|
| 앱 설정 | Kafka 설정은 앱 `application.yml` 한 곳에만 있습니다. String 직렬화, `acks=all`, `enable.idempotence=true`, `auto-offset-reset: latest`가 들어 있고 `max.block.ms`와 브로커 `offsets.retention.minutes`는 기본값입니다 |
| 도메인 yml | `spring.kafka.*`를 넣지 않습니다. 나중에 불러온 도메인 yml이 앞의 값을 조용히 덮어씁니다. 도메인 값은 자기 리스너 설정 코드에 둡니다 |
| 빈 | `KafkaTemplate`, `ProducerFactory`, `ConsumerFactory`를 도메인에서 빈으로 새로 만들지 않습니다(Boot 기본 빈이 사라집니다) |
| 빈 이름 | 도메인이 만드는 컨테이너 팩토리와 에러 처리기 빈은 도메인 이름을 앞에 붙입니다(`{도메인}KafkaListenerContainerFactory`). 이름이 겹치면 앱이 뜨지 않습니다 |
| 에러 처리기 | 자기 컨테이너 팩토리에 직접 붙입니다. 에러 처리기 빈이 앱에 둘 이상이면 Boot가 기본 팩토리에 아무것도 붙이지 않습니다 |
| 의존성 | `spring-boot-starter-kafka`를 `kidly.domain` 빌드 플러그인으로 모든 도메인이 받습니다. 도메인 빌드 파일에 따로 넣지 않습니다 |
| 보내기 스위치 | 커밋 뒤 Kafka로 보내는 빈(아웃박스를 쓰면 릴레이 스케줄러)에는 `@ConditionalOnProperty(name = "kidly.kafka.producer.enabled", havingValue = "true", matchIfMissing = true)`를 붙입니다. Kafka 컨테이너가 없는 테스트는 이 값을 꺼서 보내지 않습니다 |
| 전송 실패 로그 | 앱은 Boot 기본 `KafkaTemplate`의 전송 실패를 토픽 · 키 · `eventType` · `eventId`와 예외로 남기고 본문은 남기지 않습니다(앱 설정). 받는 쪽 DLT 전송기처럼 Boot 기본 템플릿을 쓰는 곳은 따로 할 일이 없습니다 |
| 직접 만든 템플릿 | `new KafkaTemplate<>(...)`으로 만든 템플릿에는 앱 설정이 붙지 않고, 기본 실패 로그가 키와 본문을 그대로 남깁니다. 만들 때 `setProducerListener(new ProducerListener<>() {})`로 기본 실패 로그를 끄고, 실패는 자기 코드에서 `eventType` · `eventId` · 키로 남깁니다 |
