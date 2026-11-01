# 주문 도메인 설계 규칙

주문 모듈(주문 · 장바구니 · 배송 · 조르기 컨텍스트) 안의 설계 규칙이다. 도메인 경계 · `shared` 계약 · Kafka 토픽/키/그룹 · 묻고 바꿀 것은 루트 `CLAUDE.md`를 따르고, 여기와 부딪히면 루트가 우선이다.

## 1. 컨텍스트 · 계층과 포트
- 이 모듈은 컨텍스트 넷이다: `boundedcontext.order`(주문 · 결제 반영 · 만료 · 구매확정 · 이력 · 재처리 · 아웃박스 · DLT · 관리자 · 상품/판매자/자녀 복사본), `boundedcontext.cart`(장바구니), `boundedcontext.delivery`(판매자별 배송 · 발송 · 배송 완료 · 판매 주문 조회), `boundedcontext.wish`(조르기, 아래). 저장은 모두 `orders` 스키마다
- `boundedcontext.wish`(조르기 = 부모 · 자녀 공용 장바구니, #640)는 팀이 정책을 확정하지 않아 걷어낼 수 있게 둔다: 아무 컨텍스트도 wish를 참조하지 않고(wish는 들어오는 포트가 없다), 표는 `wish_item` 하나, 스위치 `kidly.order.wish.enabled`(false면 빈 · API 없음, 모든 빈에 같은 조건), 정책은 `WishPolicy` 클래스 한 곳, 호출 주체는 `app.port.WishActor`(구현은 out)
- 컨텍스트 사이는 in · out으로만 잇는다(#641, `api` 패키지는 두지 않는다). 받는 쪽은 들어오는 포트를 자기 `app.port.in`에 두고 app이 구현한다(`OrderLookup` · `DeliveryRecorder` · `ReplicaLookup` · `CartCleanup` · `DeliveryOperations` · `ShippingDelays`). 부르는 쪽은 필요한 것을 자기 `app.port`에 자기 말로 두고(`OrderDeliveries` · `OrderedCartCleaner` · `CartCatalog` · `CartOrders` · `DeliveryOrders` · `WishCatalog`), `out.{상대}` 어댑터가 상대 `app.port.in`을 불러 값을 옮겨 담는다. 다른 컨텍스트를 아는 것은 이 어댑터뿐이다(app · domain · in은 모른다). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다. 예외는 두지 않는다(멈춘 주문의 발송 지연도 `OrderDeliveries`로 읽는다). 떼어 낼 때는 어댑터를 HTTP · Kafka 호출로, 받는 쪽은 `app.port.in`을 부르는 컨트롤러 · 리스너를 in에 더한다
- 컨텍스트마다 시각 기반 클래스(`OrderTimeEntity` · `CartTimeEntity` · `DeliveryTimeEntity` · `WishTimeEntity`) · 규칙 예외 · 오류 코드(`OrderErrorCode` · `CartErrorCode` · `DeliveryErrorCode` · `WishErrorCode`)를 따로 둔다. 같은 오류는 응답(상태 · title · detail)이 같게 둔다
- `domain`: 업무 규칙 · 불변식(엔티티 · 상태 enum · 값, JPA 엔티티 겸용). 상태 · 값을 보고 하는 업무 판단(취소 가능 · 결제 반영 결과 · 수량 상한 · 재처리 상태 등)은 domain 메서드가 하고 app은 순서만 조율한다. 기술 장치(아웃박스 메시지 · DLT 보관 · Kafka 대기량 확인 결과)는 domain 밖(out · app.port 값)에 둔다(#642). Spring과 `common`을 쓰지 않는다. 상태는 업무 메서드로만 바꾸고, 다시 와도 되는 변경은 `TransitionResult`(`APPLIED` · `ALREADY_DONE` · `NOT_APPLICABLE`)로 돌려준다
- `app`: 유스케이스 서비스. 기능별 하위 패키지(`order` · `outbox` · `deadletter` · `provided` 등)끼리는 서로 의존하지 않는다. 여러 기능이 같이 쓰는 값은 `app` 바로 아래(`OrderActor` · `OrderErrorCode` · `OrderLogContext` · `DeadLetterReplay`)에 둔다. 주문의 들어오는 포트 구현은 `app.provided`(`ProvidedOrderService` · `ProvidedReplicaService`)이고, 주문을 취소하는 판매자 취소 포트만 `app.order`(`SellerCancelService`, 대기 기록 닫기 `WaitingRecordCloser`가 거기 있어서)다. 장바구니 · 배송의 구현은 `CartService` · `DeliveryService`다
- `app.port`: 저장소 · 외부 연결 · 다른 컨텍스트 · 작업 실행처럼 나가는 것의 인터페이스, `app.port.in`: 다른 컨텍스트가 들어오는 인터페이스. 둘 다 최상위는 인터페이스만 두고, 주고받는 값은 그 인터페이스 안에 둔다(값은 record, 값의 종류는 enum, 조회 결과 행은 인터페이스. class는 두지 않는다). 나가는 포트 구현은 `out`: 저장소는 대부분 Spring Data `Jpa…Repository`가 포트를 상속하고, 아웃박스 · DLT처럼 엔티티가 out에 있는 것은 `…Store`(`out.outbox.OrderOutboxStore` · `out.deadletter.OrderDeadLetterStore`)가 포트를 구현하며 그 `Jpa…Repository`는 out 안에서만 쓴다. 들어오는 포트 구현은 app
- `in`: `web`(컨트롤러 · `dto`. 주문의 관리자 API(`/api/v1/admin/**`) 컨트롤러 · 재처리 도우미 · dto는 `web.admin`, 구매자 API는 `web`. 장바구니 · 배송 · 조르기는 나누지 않는다) · `event`(이벤트 수신) · `scheduler` · `config`(Kafka 리스너 팩토리 · DLT). `domain`과 `out`을 쓰지 않는다
- `out`(#651): `persistence`(Spring Data JPA 저장소만) · `outbox`(아웃박스 메시지 · 저장 · 포트 구현) · `kafka`(발송 · 봉투 · 결제 이벤트 대기량 확인) · `deadletter`(DLT 보관 행 · 포트 구현) · `lock`(서버 한 대 실행 DB 잠금) · `config`(Kafka 토픽 · 설정값 → `OrderPolicy`, 배송은 택배 조회 빈 · 연결 설정) · `tracker`(배송: Delivery Tracker 연동, #650) · `{상대 컨텍스트}`(어댑터)
- 설정은 계층마다 `config` 한 곳에 둔다(컨텍스트 바로 아래에 새 폴더를 만들지 않는다): 스프링 설정(`@Configuration`)은 `in.config` · `out.config`, 설정 값(`@ConfigurationProperties`)은 app · `in.config` · `out.config`. app 설정 값은 쓰는 기능 패키지에 둔다(#651-1)
- 계층 규칙은 이 모듈의 `LayerArchitectureTest`(`LayerRules`, 컨텍스트마다), 컨텍스트 사이 규칙(다른 컨텍스트 참조는 내 out → 상대 `app.port.in`만)은 `OrderContextArchitectureTest`, 패키지 배치(위 in · out · 설정 자리)는 `OrderPackageLayoutArchitectureTest`, 기능 사이 의존 · 포트 규칙은 앱의 `OrderArchitectureTest`가 검사한다

## 2. 이벤트 수신
- 같은 이벤트가 다시 와도 결과는 1회분이다: 복사본(`ReplicaProduct` · `ReplicaSeller` · `ReplicaChild`)은 저장된 `occurredAt`보다 늦은 이벤트만 반영하고, 주문 상태는 이미 그 상태면 `ALREADY_DONE`으로 끝낸다
- 필수 값이 없으면 `EventGuard`로 WARN 한 줄을 남기고 무시한다
- 받는 길은 스위치 `kidly.order.kafka.consumer.enabled` 하나로 고른다: `true`(기본)면 Kafka 리스너, `false`면 Spring 이벤트(`SpringEventReceiver`). 처리 코드(`…EventListener`)는 같다
- Kafka 리스너(`ProductEventKafkaListener` · `SellerEventKafkaListener` · `ChildProfileEventKafkaListener` · `PaymentEventKafkaListener`)는 팩토리 `orderKafkaListenerContainerFactory`(`OrderKafkaListenerConfig`)만 쓴다: 한 건씩 완료, 처음 읽는 그룹은 earliest, 실패하면 1초 간격 3번 재시도 뒤 `{토픽}.order.dlt`, 다시 해도 같은 예외 · 읽기 실패는 바로 DLT. 결제 완료는 DLT로 보내기 전에 `payment_recovery` 보관을 한 번 더 시도하고, 보관되면 보내지 않는다(`PaymentEventKafkaListener.keepAfterRetries`)
- 처리 실패를 주문이 보관하면 리스너가 처리 완료로 본다. 재고 차감됨 · 재고 차감 실패 · 결제 실패는 `EventFailureKeeper.run`이 `order_event_failure`에, 결제 완료는 `PaymentEventListener`가 `payment_recovery`에 보관하고, 둘 다 `EventFailureKeeper.markHandled`로 처리 완료 표시를 붙인다. 관리자가 재처리 · 종료한다. 보관까지 실패하면 재시도 → DLT. 결제 완료의 동시 처리에서 진 쪽은 주문이 아직 결제대기면 보관 없이 재시도하고, 재시도가 끝나면 위처럼 보관한다(재처리 대기 주문은 결제 마감 만료가 취소하지 않는다)
- DLT 메시지는 `OrderDeadLetterKafkaListener`가 `order_dead_letter`에 원래 위치(토픽 · 파티션 · 오프셋)당 1건으로 저장한다. 저장 실패는 DLT로 다시 보내지 않고 끝없이 재시도한다. 관리자 재처리는 저장한 본문으로 원래 리스너를 그대로 부른다(원래 토픽에 다시 넣지 않음)
- 주문이 취소되면 그 주문의 재처리 대기 기록을 같은 트랜잭션에서 닫는다(`WaitingRecordCloser`, 이유 `ORDER_CANCELLED`)

## 3. 이벤트 발행과 주기 작업
- 주문 이벤트는 루트 규칙대로 `EventPublisher`로 발행하고, 같은 트랜잭션에서 `order_outbox`에 저장된다(`OrderOutboxWriter`). 업무가 롤백되면 메시지도 남지 않는다
- 장바구니는 담기에 성공하면(새 줄 · 같은 줄 합치기) `cart.item-added`(`CartItemAddedEvent`, 키 = 회원 ID)를 같은 길로 낸다: `cart/app/port/CartEvents` → `cart/out/order` 어댑터 → 주문 `app/port/in/OrderEventIssuer`(구현 `ProvidedEventService`, #654). 받는 도메인에 맞춘 필드는 넣지 않는다
- 관리자 멈춘 주문(아웃박스 멈춤)은 eventType 접두(`order.` · `order-item.`)로 메시지 키가 주문 ID인지 판별한다 — 새 주문 이벤트 종류가 이 접두를 벗어나면 `StuckOrderService` 목록을 같이 고친다
- `OrderOutboxRelayScheduler`가 안 보낸 메시지를 한 트랜잭션에서 id 순서로 batch만큼 보내고(`KafkaOrderEventSender`), 보내다 실패하면 그 메시지에서 멈춘다. 보낸 메시지는 `OrderOutboxCleanupScheduler`가 보관 기간 뒤 한 트랜잭션의 delete 한 번으로 지운다
- 주기 작업(결제 마감 만료 · 자동 구매확정 · 아웃박스 릴레이 · 정리)은 `SingleServerJobRunner`로 한 번에 한 서버에서만 돈다(PostgreSQL advisory lock `kidly.order.{작업}`). 작업은 잠금 트랜잭션 밖에서 돈다
- 주기 작업(결제 마감 만료 · 자동 구매확정 · 택배 조회)은 메모리 순환 커서(`RotatingCursor`, #656)로 지난 실행이 상한(max-per-run)에서 멈춘 곳 다음부터 이어 읽고 끝에 닿으면 처음으로 돌아간다(서버마다 따로, 재기동하면 처음부터). 상한에 닿고 남은 대상이 있는 실행의 실행 로그는 WARN 한 줄이다
- 결제 마감 만료 · 자동 구매확정(`OrderScheduler`)은 대상 주문 하나 = 트랜잭션 하나다. 한 건이 실패해도 로그만 남기고 다음 건을 처리한다
- 결제 마감 만료는 결제 이벤트를 Kafka로 받는 동안 처리 대기분이 있으면 그 주기를 건너뛴다(`PaymentExpiryGate`)
- 배송 자동 완료(#650): `DeliveryStatusPoller`가 발송 중이고 조회를 멈추지 않은 배송을 `DeliveryStatusChecker`(out `tracker`, Delivery Tracker)로 트랜잭션 밖에서 조회하고, 택배사 DELIVERED이고 택배사 시각 > 발송 시각일 때만 `Delivery.completeByTracking`으로 완료한다(완료 시각 = 택배사 시각, 이력 SYSTEM · `scheduler:delivery-tracking`, 수동 완료와 겹쳐도 1회). 운송장 형식 오류는 `tracking_stopped_at`으로 조회를 멈추고(수동 완료는 가능) 그 밖의 오류는 다음 주기에 다시 본다. 한 서버에서만(`DeliveryJobRunner`, 잠금 `kidly.delivery.{작업}`), 인증값이 없으면 기능만 꺼진다
- 판매자 발송 전 부분 취소(#655, 스위치 `kidly.order.partial-cancel.enabled` 기본 false): `Delivery.cancelBySeller`(READY만) → `delivery/out/order` 어댑터 → 주문 `app/port/in/SellerItemCancellation`(구현 `app.order.SellerCancelService`)이 주문을 FOR UPDATE로 읽고(동시 취소 직렬화, 주문 → 배송 잠금 순서) `Order.cancelSellerItems`로 그 판매자 주문상품을 CANCELLED, 모두 취소면 주문도 CANCELLED(`SELLER_CANCELLED`)로 바꾸고 취소 기록(`order_item_cancellation`, ID = cancellationId) · 이력 · `order-item.cancelled`(키 = 주문 ID)를 한 트랜잭션으로 남긴다. 이때 `order.cancelled`는 보내지 않고, 주문이 취소됐으면 재처리 대기 기록을 닫는다. 취소된 주문상품은 배송 완료 기록 · 구매확정 · 자동 구매확정 · 멈춘 주문에서 빠진다

## 4. 테스트
- 통합 · 기능 · Kafka 테스트는 이 모듈에서 돈다. 주석 · 컨테이너 · 정리는 주문 모듈 공통 `boundedcontext.support`(#651, 앱의 `com.bbcc.kidly.support`와 다른 패키지)(`IntegrationTest` · `FunctionalTest` · `KafkaIntegrationTest`)를 쓰고, 테스트 앱은 `OrderTestApplication`(주문 모듈 네 컨텍스트 · `common`만)이다
- 주문 테스트는 다른 도메인 코드를 쓰지 않는다(`OrderTestIndependenceTest`). 다른 도메인이 하던 반응은 테스트가 이벤트로 직접 넣는다
  - `OrderEventDelivery`: 다른 도메인 이벤트를 주문에 넣는다(수신 방식이 바뀌면 이것만 고친다)
  - `PaymentEvents`: 결제 쪽이 보내던 결제 완료 · 결제 실패
  - `OrderFlowEventRecorder`: 주문이 발행한 이벤트 확인
- Kafka 수신 테스트는 `OrderKafkaTestClient`로 보내고, 리스너는 `OrderKafkaListenerTestGuard`에서 꺼내 그 테스트 안에서만 켜고 끈다(가드가 없으면 다른 테스트가 남긴 메시지를 처리한다)
- 동시성 테스트는 `ConcurrentCalls`로 동시에 부르고, 결과가 하나로 수렴하는지(1회분 · 진 쪽 예외 종류)를 본다. 잠금 순서를 정해야 하면 테스트가 다른 트랜잭션에서 행을 잡아 순서를 만든다
- 실패 주입은 테스트 안 DDL(`CHECK … NOT VALID`, 표 이름 바꾸기)로 하고 테스트 끝에 되돌린다. 로그 · MDC는 `CapturedLogs`로 본다

## 5. 관리자 확인 대상
- 관리자 화면 · 목록 · WARN/ERROR 로그에는 사람이 실제로 조치해야 하는 것만 남긴다
- DLT 메시지 목록은 상태를 주지 않으면 `OPEN`만 보인다(처리 · 종료된 건은 상태로 걸러 본다). 결제 토픽에서 온 메시지가 먼저 온다(`paymentEvent`). 이벤트 처리 실패 · 결제 반영 실패 목록은 상태를 주지 않으면 모든 상태가 보인다
- 이미 해결됐거나 저절로 맞춰지는 건은 그 자리에서 닫거나(예: 주문 취소 시 대기 기록) 로그 수준을 내린다(예: 같은 메시지의 반복 저장 실패는 첫 번만 ERROR, 이후 DEBUG)
- 로그는 결과 하나에 한 줄이고, 본문 · 예외 메시지 · 개인정보를 넣지 않는다(eventType · eventId · 키 · ID · 예외 종류만)

## 6. 로그 추적(MDC)
- Kafka 수신 · 아웃박스 릴레이 · 주문 스케줄러는 메시지 · 대상 하나를 처리하는 동안 `OrderLogContext`로 MDC에 추적 값을 넣고, 끝나면(예외 포함) 이전 값으로 되돌린다
- 요청 밖이라 `traceId`가 비어 있으면 `correlationId`를 넣는다. HTTP 요청 안에서는 요청의 `traceId`를 그대로 둔다
- Kafka 수신은 팩토리의 `OrderLogContextRecordInterceptor`(헤더 eventId · eventType, `correlationId` = 키), DLT 관리자 재처리는 리스너를 직접 부르므로 `KafkaListenerDeadLetterReplayer`가 같은 값을 따로 연다. 아웃박스 릴레이는 메시지의 eventId · eventType · `correlationId`(= 메시지 키), 주문 스케줄러(`OrderScheduler`)는 `correlationId`(= 대상 주문 ID)만 넣는다
