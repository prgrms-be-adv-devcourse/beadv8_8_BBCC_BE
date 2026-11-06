# 옮기는 순서

## 받는 쪽을 옮기기 전에 끝나 있어야 하는 것

1. 결제 마감 만료 · 정산 스케줄러의 실행 조건(6절 미정): order, settlement가 받는 쪽을 옮기기 전
2. 상품 샘플 데이터 러너 수정: `ProductSampleDataService`는 판매자 복사본이 없으면 샘플을 조용히 건너뜁니다. 복사본이 Kafka로 늦게 채워지면 샘플이 생기지 않습니다

## 순서

1. **보내기 켜기**: 아직 보내지 않는 도메인(order, settlement)이 토픽 선언과 보내기를 넣습니다. Spring 이벤트는 그대로 두므로 동작은 바뀌지 않습니다. dev에서 그 도메인의 흐름을 한 번 실행해 토픽, 키, 본문, 헤더를 확인합니다.
2. **받는 쪽을 하나씩 옮긴다**: recommend → settlement → product → payment → order → notification · childprofile · seller. 그 이벤트를 보내는 도메인이 1을 마친 뒤에 시작합니다.

## 받는 쪽을 옮기는 PR마다 확인할 것

- [ ] 보내는 도메인이 그 토픽으로 이미 보내고 있다
- [ ] 토픽마다 리스너 클래스가 하나다
- [ ] 같은 이벤트의 `@DomainEventListener`를 같은 PR에서 지웠다
- [ ] 같은 메시지 두 번 → 결과 1회분 테스트가 있다
- [ ] 발행 직후 결과를 단언하던 통합 테스트를 `@KafkaIntegrationTest`로 옮기고 `support.AsyncWait.until(...)`로 기다리게 바꿨다
- [ ] 수신 테스트는 그 테스트 안에서만 리스너를 켜고 끄며(`KafkaListenerEndpointRegistry`), 테스트마다 고유한 키를 쓰고 그 키의 결과만 확인한다
- [ ] 테이블을 비우기 전에 처리 중인 메시지가 끝나기를 기다린다(첫 수신 PR에서 넣는다)
- [ ] 받는 쪽 DLT `NewTopic`이 있고, DLT 전송기 목적지가 `{토픽}.{받는도메인}.dlt`, 파티션 `-1`이다
- [ ] 로그 · 예외 메시지에 본문 값이 없다
- [ ] 배포 뒤 그 그룹의 lag 0, 빈 DLT, 옮긴 흐름이 dev에서 실제로 처리됐는지 확인한다고 PR에 적었다. `latest`라서 첫 배포 때 새 앱이 파티션을 배정받기 전에 들어온 메시지는 읽지 않습니다

## 도메인별 주의

| 도메인 | 주의 |
|---|---|
| recommend | `applyIfNewer`에서 값 검사보다 `occurredAt` 비교를 먼저 하도록 바꿉니다 |
| settlement | 처리가 늦으면 구매확정분이 다음 달 정산으로 밀립니다. 정산 스케줄러의 실행 조건이 먼저 정해져야 합니다 |
| product | 주문 이벤트를 받는 두 클래스를 `kidly.order.events` 리스너 하나로 합칩니다 |
| payment | 가입 직후 지갑이 아직 없을 수 있습니다(`WALLET_NOT_FOUND`) |
| order | 결제 마감 만료 스케줄러가 lag을 확인한 뒤 실행해야 합니다(정상 주문의 잘못된 만료 방지). 결제 완료 반영은 자기 트랜잭션으로 처리합니다 |
| 화면 | 응답 직후 GET은 중간 상태를 볼 수 있습니다. 판매자 승인 직후 토큰 재발급, 자녀 등록 직후 주문처럼 다른 도메인 결과에 기대는 화면은 그 리스너를 옮길 때 확인합니다 |

## 마무리

- ArchUnit 규칙을 넣습니다. `@DomainEventListener`는 자기 도메인 이벤트만 받습니다(member ← `seller.changed`는 예외 목록에 이름으로 적습니다). 기존 리스너 위치 규칙(예: `OrderArchitectureTest`)의 검사 대상에 `@KafkaListener`를 더합니다.
- member는 서비스 분리 때 Gateway가 받을 토큰 무효화 이벤트를 `kidly.member.events`로 보내는 것까지 만들어 둡니다.

## 서비스로 떼어 내기 전

- 그 도메인이 받는 이벤트와 보내는 이벤트가 모두 Kafka로 옮겨졌는지 확인합니다. Spring 이벤트는 받는 쪽이 없어도 오류 없이 버려집니다.
- 떼어 낸 서비스의 Kafka 접속 방법을 인프라 담당과 정합니다. 브로커는 t3.medium k3s(`deploy/kafka`)에 있고, 서버에서는 VPC 안의 `KAFKA_BOOTSTRAP_SERVERS`(medium 사설 IP:NodePort)로 접속합니다.
- 토픽, 키, 메시지 모양, 그룹 이름은 바꾸지 않습니다.
