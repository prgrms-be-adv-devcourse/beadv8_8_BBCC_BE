# 상품 도메인

상품 도메인의 규칙 · 결정은 루트가 아니라 이 파일에 둔다. 코드를 읽으면 알 수 있는 동작은 적지 않고, 코드만 봐서는 모르는 제약과 정한 이유만 적는다. 자세한 논의는 절 제목의 이슈에 있다.

## 고치기 전에 지킬 것
- `product.changed`는 바뀐 뒤의 전체 값을 담는다. 받는 쪽은 저장된 값보다 `occurredAt`이 늦을 때만 덮어쓴다
- 판매대기 상품은 `product.changed`를 보내지 않는다. 다른 도메인은 판매를 시작한 상품만 안다(SALE-08, 판단은 `Product.hasStartedSale()`). 등록 취소한 상품은 판매대기에서 바로 취소했어도 보낸다(아래 「등록 취소」)
- 이벤트 record의 칸은 추가만 하고 기존 생성자를 남긴다. 받는 도메인의 테스트가 이벤트를 직접 만들기 때문이다. 칸을 더하면 받는 쪽에 알린다
- 메시지는 한 번 이상 갈 수 있다. 받는 쪽은 `eventId` · `occurredAt` · 업무 상태로 거른다
- Kafka 리스너는 토픽마다 클래스 하나다. 같은 토픽 · 같은 그룹에 둘이면 메시지를 나눠 받는다
- 자기 이벤트 `product.changed`는 Kafka로 받지 않는다
- 로그 · 예외 메시지에는 `eventType` · `eventId` · 키 · 예외 종류만 남기고 본문은 남기지 않는다(DB 오류 메시지에도 행 값이 들어갈 수 있다)
- 상품 아웃박스 릴레이는 한 프로세스에서만 돈다(잠금이 없다). 상품 서비스만 돌린다(아래 「상품 서비스」)

## 이벤트
| 보내는 이벤트 | 보내는 곳 | 보내는 때 | 받는 도메인 |
|---|---|---|---|
| `product.changed` | `app/product/support/ProductChangedPublisher` | 판매 시작 · 중지, 등록 취소 · 되돌리기, 판매 중 상품 수정, 품절 여부 변경(`SoldOutPublisher`), 관리자 다시 보내기 | 주문, 추천, 검색, 커뮤니티, 알림 |
| `stock.deducted` | `app/inventory/support/InventoryDeductor` | 주문 생성으로 재고를 모두 차감했을 때 | 주문 |
| `stock.deduction-failed` | `app/inventory/support/InventoryDeductionRecorder` | 재고가 모자라거나 판매 중이 아니어서 거절했을 때 | 주문 |

| 받는 이벤트 | 토픽 · 받는 곳 | 하는 일 |
|---|---|---|
| `order.created` | `kidly.order.events` · `in/inventory/ProductOrderKafkaListener` | 재고 차감. 결과로 `stock.deducted` 또는 `stock.deduction-failed` |
| `order.cancelled` | 같은 리스너 | 차감한 재고 복구 |
| `order-item.cancelled` | 같은 리스너 | 판매자가 발송 전에 취소한 주문상품의 재고 복구(#693) |
| `seller.changed` | `kidly.seller.events` · `in/replica/ProductSellerKafkaListener` | 판매자 복사본(`replica_seller`) 갱신. 상세 · 목록의 상호명 |

- 주문 · 판매자 이벤트는 Kafka로만 받는다. 상품 서비스에는 주문 이벤트를 Spring 이벤트로 내는 곳이 없어 Spring 리스너를 두지 않는다

## Kafka 발행 (아웃박스, #551 · #565)
- 커밋 직전(`BEFORE_COMMIT`)에 상품이 발행한 이벤트(`producer() = product`)만 `product_outbox`에 저장한다. 업무와 같은 트랜잭션이라 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 롤백된다
- 릴레이(`ProductOutboxScheduler` → `ProductOutboxRelayService`)는 1초마다 id 순서로 100건을 보낸다. 실패하면 멈췄다가 다음 실행에 같은 메시지부터 보낸다. 매일 04:00(한국 시간)에 7일보다 먼저 보낸 메시지를 지운다
- 릴레이는 트랜잭션 없이 돈다. 브로커가 멈추면 메시지 하나에 최대 약 17초(`max.block.ms` 5초 + 응답 12초)를 기다리는데, 그동안 DB 연결을 쥐지 않기 위해서다
    - 결과 저장은 Spring Data `save`의 기본 트랜잭션에 기댄다. 직접 선언한 쿼리(`@Modifying` 조건부 UPDATE 등)로 바꾸면 트랜잭션 조각 빈(`app/outbox/support`)을 둔다
- 릴레이 잠금(advisory lock)은 없다. 한 프로세스 · 스케줄러 스레드 하나라 겹치지 않는다. 인스턴스를 늘리거나 두 프로세스에서 함께 돌려야 하면 더한다
- 스케줄러 스레드는 하나뿐이다. 상품 서비스에서는 릴레이와 정리만 쓰므로, 브로커 장애 중에는 정리 작업이 늦어질 수 있다
- 전송 실패 · 릴레이 · 정리 예외는 `ERROR`다. 한 건이 막히면 뒤 상품 이벤트가 모두 멈추고, Grafana "ERROR 로그" 알림은 ERROR 건수만 센다. 결제 아웃박스는 같은 경우를 `WARN`으로 남긴다(10-30 기준, 결제에 맞출지 물음)
    - 실패 로그는 실패 기록을 저장하기 전에 남긴다. DB도 함께 장애일 때 원인이 사라지지 않게 하기 위해서다
- `kidly.kafka.producer.enabled=false`면 스케줄러가 없고 저장만 한다(`ProductOutboxRelaySwitchTest`). 두 프로세스가 함께 돌던 때 쓰던 릴레이 스위치(`kidly.product.outbox.relay.enabled`)는 지웠다. 테스트 설정은 릴레이 주기를 길게 두고 정리를 끄며, 테스트가 서비스를 직접 부른다
- 운영
    - 로그 `{env="dev"} |= "상품 아웃박스"`(보낸 메시지가 있는 실행만 `sent=N`). 지표는 `client_id=~"kidly-product-outbox-.*"`(Spring Kafka가 `-번호`를 붙인다)
    - 브로커 장애 동안 ERROR는 시간당 약 200 ~ 600줄, Slack은 처음 한 번 뒤 4시간마다다. 알림 규칙이 환경마다 하나라 그동안 다른 ERROR는 새 알림을 만들지 못한다

## Kafka 수신 (#692 · #693)
- 구독 그룹 `kidly.product`, 팩토리 `in/ProductKafkaListenerConfig`. 헤더 `eventType`으로 고르고, 받지 않는 종류는 건너뛰며, 헤더가 없으면 DLT로 보낸다
- 실패하면 1초 간격으로 3번 더 시도한 뒤 `{토픽}.product.dlt`(파티션 `-1`)로 보낸다. 읽지 못한 본문과 다시 해도 같은 예외(NPE · `IllegalArgumentException` · `IllegalStateException` · `BusinessException`)는 바로 보낸다
- 스위치 `kidly.product.kafka.consumer.enabled`(환경 변수 `PRODUCT_KAFKA_CONSUMER_ENABLED`, 기본 true, 테스트 false). true면 Kafka 리스너가 시작하고, false면 시작하지 않는다(테스트가 필요한 리스너를 직접 켠다). 서버에서 false로 두면 수신이 멈춰 재고 차감 · 결제 요청도 멈추므로 장애를 되돌리는 용도로 쓰지 않는다. 시작 여부는 `spring.kafka.listener.auto-startup`이 아니라 리스너 애노테이션의 `autoStartup`이 정한다
    - false 동안 Kafka에 쌓인 메시지는 true로 올릴 때 처리된다
- 두 번 받아도 1회분이다. 판매자 복사본은 저장된 시각보다 늦은 변경만 반영하고, 주문 생성됨은 주문상품마다 차감 기록이 한 줄(UNIQUE)이다
- 키가 주문 ID라 한 주문의 이벤트는 순서대로 처리한다. 취소가 생성보다 먼저 오면 FAILED를 남기고 뒤에 온 생성은 차감하지 않는다(INV-09)
- 읽기 시작 위치는 팀 규칙대로 `latest`다. 첫 배포 때 파티션 배정 전에 나간 메시지는 받지 못한다
    - 주문 이벤트는 다시 보내기 API가 없다. 배포 뒤 결제 대기로 넘어가지 않은 주문이 있는지 확인한다
    - 판매자 변경은 판매자의 `POST /api/v1/admin/sellers/changed-events`(#643)로 다시 보낸다
- 상품 DLT에는 받는 곳과 재처리가 없다. 차감한 주문의 취소가 DLT로 빠지면 재고가 저절로 돌아오지 않는다. DLT 로그의 키(주문 ID)로 차감 기록을 확인하고 사람이 고친다
- 테스트에서는 `ProductKafkaListenerTestGuard`가 상품 리스너 2개를 레지스트리에서 빼 들고 있다. 컨텍스트를 다시 쓸 때 `registry.start()`가 `autoStartup`과 상관없이 리스너를 켜는 것을 막기 위해서다. 수신 테스트는 가드에서 자기 리스너만 꺼내 켠다
- 로컬은 Kafka가 떠 있어야 판매자 복사본이 생긴다. DB를 새로 만든 첫 기동에서는 복사본이 샘플 데이터보다 늦게 생겨 샘플이 건너뛰어질 수 있다(그대로 두기로 함). 다시 기동하고, 판매자 메시지를 못 받았으면 판매자 다시 보내기 API를 먼저 부른다

## 판매자 부분 취소의 재고 복구 (InventoryRestorationService, #693)
- 주문상품 ID로 차감 기록을 읽어 `InventoryRestoration`이 `DEDUCTED`만 고르고, `InventoryRestorer`가 조건부 UPDATE로 되돌린다. 마지막 몫이 취소돼 주문 전체가 취소될 때도 `order.cancelled` 없이 이 이벤트만 온다
- 이미 `RESTORED`인 주문상품은 다시 되돌리지 않는다. 같은 부분 취소를 두 번 받거나 뒤에 주문 취소됨이 와도 안전하다
- 판매자 취소는 `PAID` 주문에서만 되고 결제 요청은 `stock.deducted` 뒤에만 나가므로, 그 주문상품에는 `DEDUCTED` 기록이 있어야 한다. 아래 경우는 도메인 사이 버그로 보고 ERROR 로그(`orderId` · `cancellationId`)를 남긴다. 기록이 없거나 모두 `FAILED`면 재고와 기록을 바꾸지 않는다. 다시 받아도 같으므로 DLT로 보내지 않는다
    - 기록이 없음: 주문 취소됨이 먼저 온 경우(INV-09)와 달리 `FAILED`를 남기지 않는다. 결제된 주문이라 뒤늦게 주문 생성됨이 올 수 없어 막을 것이 없다(11-02 정석님)
    - 기록이 모두 `FAILED`: 재고 없이 결제된 주문이라는 뜻이라 더 위험하다
    - 일부만 기록이 없음: 기록이 있는 `DEDUCTED`는 되돌리고, 없는 주문상품 ID를 ERROR 로그에 남긴다(`InventoryRestoration.orderItemIdsWithoutRecord`, 11-02 정석님). 주문 취소됨 경로는 아직 이 확인을 하지 않는다
- 수량 일부 취소는 없다. 차감 기록을 통째로 `RESTORED`로 바꾼다
- 배포 순서: 상품을 먼저 배포한 뒤 주문의 판매자 취소 스위치(`kidly.order.partial-cancel.enabled`)를 켠다

## 등록 취소 (Product.delist, #904)
판매자가 상품을 더 팔지 않겠다고 내리는 상태를 판매중지(`STOPPED`)와 다른 판매 상태 `DELISTED`로 둔다. 판매중지는 판매자가 다시 열 수 있지만 등록 취소는 되돌릴 수 없다. 판매자가 되돌릴 수 있으면 판매중지와 가를 기준이 없어 상태를 따로 둘 까닭이 없다(11-05 정석님).

- 그 상품을 등록한 판매자만, 판매대기 · 판매중 · 판매중지 어느 상태에서나 등록 취소한다. 관리자가 판매중지한 상품도 된다. 판매대기 상품도 지우지 않고 상태만 바꾼다(지우면 옵션 · 실측 · 재고 행을 함께 지워야 한다)
- 이미 등록 취소한 상품을 다시 등록 취소하면 바꾸지 않고 성공한다(`false`). 판매중지 반복과 같이 재시도를 받아 준다
- 판매자는 되돌릴 수 없고, 관리자만 `revertDelistByAdmin()`으로 판매중지로 되돌린다. 판매중으로 되돌리면 판매자가 원하지 않는 판매가 시작되고 판매 시작 조건 검사를 건너뛰기 때문이다. 다시 팔지는 판매자가 판매 시작으로 정한다. 등록 취소하지 않은 상품을 되돌리면 409(`PRODUCT_NOT_DELISTED`)다
- 관리자 판매중지 표시(`stoppedByAdmin`)는 등록 취소와 되돌리기가 건드리지 않는다. 관리자가 판매중지했던 상품은 되돌린 뒤에도 판매자가 다시 열 수 없다
    - 판매대기에서 등록 취소한 상품도 되돌리면 판매중지가 되어 카테고리를 바꿀 수 없다. 드문 경우라 받아들였다
- 등록 취소한 상품의 판매 상태 변경(판매자 · 관리자 모두), 상품 수정, 옵션 · 실측 추가, 판매자 재고 조정은 409(`PRODUCT_DELISTED`)다. 등록 취소와 되돌리기는 각자 전용 메서드로만 한다
    - 검사는 `Product.validateNotDelisted()` 한 곳이다. 상품을 바꾸는 공개 메서드를 더하면 소유자 검사 바로 뒤에 부른다. 판매 시작 · 중지 내부(`openSale` · `markStopped`)는 이 상태를 모르고, 진입점이 먼저 막는다
    - 다른 판매자의 상품이면 409보다 403(`NOT_PRODUCT_OWNER`)이 먼저다
    - 같은 값을 보낸 수정도 409다. 바뀌었는지 보기 전에 검사한다
    - 판매자 판매 시작에서는 관리자 판매중지(`SALE_STOPPED_BY_ADMIN`)보다 등록 취소가 먼저다. 판매자에게는 "새로 등록하라"가 "관리자에게 문의하라"보다 맞는 안내다
    - 재고 조정은 `ProductOption.validateAdjustableBy`가 403 다음 409 순서로 검사한다. 주문 취소에 따른 재고 복구는 시스템이 하는 일이라 막지 않는다
- 새 주문은 차감에서 거절된다. 차감은 판매중 상품만 통과시키므로 따로 막지 않는다
- API(`ProductSaleService`): 판매자 `PUT /api/v1/seller/products/{productId}/delisting`(200, 판매 상태 변경과 같은 응답), 관리자 되돌리기 `DELETE /api/v1/admin/products/{productId}/delisting`(200, 상태 `STOPPED`). 판매 상태 변경 API(`sale-status`)는 그대로이고, 등록 취소 상품에 보내면 409다. 요청 값 검증(`ON_SALE`/`STOPPED`만)도 그대로다
    - 둘 다 상태를 바꾼 같은 트랜잭션에서 `product.changed`를 보낸다(아웃박스도 같은 트랜잭션). 등록 취소는 바뀌었을 때만 보내므로 다시 등록 취소해도 이벤트가 나가지 않고, 되돌리기는 늘 바뀌므로 늘 보낸다. 판매대기에서 바로 등록 취소해도 보낸다
    - 새 enum 값 `DELISTED`는 받는 서비스(주문 · 검색 · 추천 · 커뮤니티 · 알림)가 모두 배포된 뒤에 보낸다. 그 전에 이 API나 다시 보내기(`changed-events`)로 등록 취소 상품의 메시지를 보내면, 검색 · 추천 · 주문 · 알림은 모르는 값을 역직렬화하지 못해 DLT로 간다(커뮤니티는 null로 읽는다)
    - 관리자 판매 상태 변경이 등록 취소 상품에 409를 낼 때도 `PRODUCT_DELISTED` 문구("다시 팔려면 상품을 새로 등록해 주세요")를 그대로 쓴다. 관리자는 되돌리기로 판매중지로 돌린 뒤 판매 상태를 바꾼다
    - 상품 수정에서는 소유자 검사(403) 다음에 이미지 검사(400)가 나고, 등록 취소 검사(409)는 그 뒤다. 등록 취소 상품에 잘못된 이미지를 보내면 409가 아니라 400을 받는다
    - 재고 조정과 등록 취소가 동시에 오면 상품 행을 잠그지 않아 조정이 한 번 더 들어갈 수 있다. 받아들였다
- 판매대기에서 바로 등록 취소한 상품도 다른 도메인에 알리는 상품이다(`hasStartedSale()`가 `true`). 받는 쪽이 팔 수 없는 상품으로 알아야 하기 때문이다. `hasStartedSale()`은 상태마다 알릴지 정하도록 `default` 없는 `switch`로 두어, 새 상태를 더하면 정하기 전까지 컴파일되지 않는다

## 상품 정보 변경됨의 브랜드 · 설명 · 사이즈별 실측 (ProductChangedData, #610)
검색 색인과 추천 복사본이 상품 DB를 읽지 않고 이 이벤트로만 채우므로 `data`에 `brand` · `description` · `sizeSpecs`를 더했다.

- `sizeSpecs`는 실측 행(`size_spec`)마다 하나다(`size`, `minHeightCm`, `maxHeightCm`, `measurements`). 옵션 단위로 두면 색상만 다른 옵션마다 같은 실측이 반복되어 사이즈 단위로 둔다
- 사이즈 무관 상품도 FREE 행의 실측을 담는다(실측은 필수다, SIZE-05). 빈 목록은 실측 행이 없는 상품뿐이다(11-02 정석님)
- 새 칸이 없는 예전 메시지를 읽으면 `sizeSpecs`는 compact 생성자가 빈 목록으로 바꾸고, `brand` · `description`은 null이다(11-02 정석님)
- 설명이 본문에 실리므로 등록 · 수정 요청의 설명을 5,000자로 제한한다. 1MB를 넘는 설명 하나가 아웃박스 릴레이를 멈추고 뒤의 재고 이벤트까지 막을 수 있어서다(11-02 정석님)
- `shared`는 JDK만 써서 JSON 왕복은 앱 테스트에서 확인한다(`ProductSaleStartApiTest`의 아웃박스 본문, `ProductChangedEventJsonIntegrationTest`)

## 상품 정보 변경됨 다시 보내기 (ProductChangedRepublishService, #577)
검색 구독 그룹은 `latest`라 처음 붙기 전에 나간 메시지를 읽지 않는다. 관리자가 `POST /api/v1/admin/products/changed-events`로 다른 도메인에 알리는 상품(판매중 · 판매중지 · 등록 취소)마다 `product.changed`를 한 번씩 다시 보낸다. 판매대기 상품은 보내지 않는다. 검색 수신을 배포한 뒤와 검색 색인을 새로 만들 때 환경마다 한 번 부른다.

- 기존 `ProductChangedPublisher`로 만들어 토픽 · 키 · 형식이 다른 발행과 같다. `eventId`는 새로 만들고(아웃박스는 같은 `eventId`를 한 번만 보낸다), `occurredAt`은 다시 보낸 시각이다. 받는 쪽은 반영하지만 `data`가 지금 상품 그대로라 값은 바뀌지 않는다
- 전체를 한 트랜잭션에서 보낸다. 실패하면 아웃박스에 아무것도 남지 않으니 다시 부른다. 여러 번 불러도 받는 쪽 값은 같다
- 만든 이벤트를 조회하는 API가 없어 `Location` 없이 200 `{ "publishedCount": N }`이다
- 주의
    - 다시 보내는 도중 같은 상품이 바뀌는 경우는 고려하지 않았다(10-30 정석님). 읽은 뒤 커밋 전에 판매자 수정이 커밋되면, 늦은 시각을 단 옛 값이 다음 변경 때까지 남는다. 수정이 적은 시간에 부른다
    - 상품마다 옵션 · 정보 값 · 실측 · 재고를 따로 읽고, 커밋 뒤 요청 스레드에서 다른 도메인 리스너가 상품마다 돈다. 상품이 수백 개를 넘으면 나눠 보내기나 batch fetch를 검토한다. 504를 받아도 이미 커밋됐을 수 있고, 다시 불러도 해가 없다
    - 상품 하나의 데이터가 깨지면 매번 전체가 500이다. 그 상품을 고친 뒤 다시 부른다

## 검색 분리 (10-29)
- 순서: 상품 이벤트 Kafka 발행 → 검색 서비스가 `product.changed`로 색인 · 다시 보내기(#577) → 목록 경로를 검색 서비스로 전환 → 상품의 `search` 코드 삭제(#796) → `product_search_document` 삭제 마이그레이션(#791)
- 표는 엔티티를 지운 뒤 Flyway로 지운다. `ddl-auto: validate`라 엔티티가 남은 채 표를 먼저 지우면 앱이 뜨지 않는다

## 상품 서비스 (apps/product-service, #791)
상품 도메인은 이 서비스에만 있다.
- 릴레이: 상품 서비스가 상품 아웃박스 릴레이를 돈다(`ProductServiceKafkaIntegrationTest`가 스케줄러 빈을 확인한다). 릴레이에는 잠금이 없다. 배포 때 compose가 app을 먼저 새로 띄운 뒤 상품 서비스를 띄우므로, 옛 app의 릴레이와 새 서비스의 릴레이가 겹치지 않는다
- 수신: 구독 그룹 `kidly.product`는 상품 서비스만 쓴다. 수신 스위치 `PRODUCT_KAFKA_CONSUMER_ENABLED`는 상품 서비스에서만 바꾼다
- 마이그레이션: 서비스는 자기 이력 표 `flyway_schema_history_product`를 쓰고 baseline-version `202610292246`(고정)에서 baseline을 잡는다. baseline은 처음 기동 때 한 번만 잡혀 올려도 소용없으므로 새 상품 마이그레이션은 baseline보다 뒤 버전으로 더한다. 빈 DB에서는 서비스의 init부터 마이그레이션을 적용한다(`ProductMigrationIntegrationTest`)
- Gateway · 배포: Gateway는 상품 API(공개 · 판매자 · 관리자)를 `KIDLY_PRODUCT_URI`(기본값 `http://product-service:8080`)로 보낸다. 배포는 compose profile `product`(힙 192m · 상한 512m)이고 dev · prod 모두 켰다(prod는 첫 main 배포 때 뜬다). Swagger '상품' 문서는 Gateway의 `/v3/api-docs/product`가 서비스 문서를 넘긴다
- 남은 일: 첫 prod 배포 전에 서버 메모리 대비(#793)가 들어가 있어야 한다
