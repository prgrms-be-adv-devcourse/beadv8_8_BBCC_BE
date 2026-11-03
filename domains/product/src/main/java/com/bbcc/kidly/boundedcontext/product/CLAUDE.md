# 상품 도메인

상품 도메인의 규칙 · 결정은 루트가 아니라 이 파일에 둔다. 코드를 읽으면 알 수 있는 동작은 적지 않고, 코드만 봐서는 모르는 제약과 정한 이유만 적는다. 자세한 논의는 절 제목의 이슈에 있다.

## 고치기 전에 지킬 것
- `product.changed`는 바뀐 뒤의 전체 값을 담는다. 받는 쪽은 저장된 값보다 `occurredAt`이 늦을 때만 덮어쓴다
- 판매대기 상품은 `product.changed`를 보내지 않는다. 다른 도메인은 판매를 시작한 상품만 안다(SALE-08, 판단은 `Product.hasStartedSale()`)
- 이벤트 record의 칸은 추가만 하고 기존 생성자를 남긴다. 받는 도메인의 테스트가 이벤트를 직접 만들기 때문이다. 칸을 더하면 받는 쪽에 알린다
- 메시지는 한 번 이상 갈 수 있다. 받는 쪽은 `eventId` · `occurredAt` · 업무 상태로 거른다
- Kafka 리스너는 토픽마다 클래스 하나다. 같은 토픽 · 같은 그룹에 둘이면 메시지를 나눠 받는다
- 자기 이벤트 `product.changed`는 Kafka로 받지 않는다
- 로그 · 예외 메시지에는 `eventType` · `eventId` · 키 · 예외 종류만 남기고 본문은 남기지 않는다(DB 오류 메시지에도 행 값이 들어갈 수 있다)
- 상품의 `search` 폴더(`app/port/search`, `app/search`, `domain/search`, `in/search`, `out/search`)에는 새 기능을 더하지 않는다. 검색 서비스로 옮기는 중이다

## 이벤트
| 보내는 이벤트 | 보내는 곳 | 보내는 때 | 받는 도메인 |
|---|---|---|---|
| `product.changed` | `app/product/support/ProductChangedPublisher` | 판매 시작 · 중지, 판매 중 상품 수정, 품절 여부 변경(`SoldOutPublisher`), 관리자 다시 보내기 | 주문, 추천, 검색, 상품 자신(검색용 표) |
| `stock.deducted` | `app/inventory/support/InventoryDeductor` | 주문 생성으로 재고를 모두 차감했을 때 | 주문 |
| `stock.deduction-failed` | `app/inventory/support/InventoryDeductionRecorder` | 재고가 모자라거나 판매 중이 아니어서 거절했을 때 | 주문 |

| 받는 이벤트 | 토픽 · 받는 곳 | 하는 일 |
|---|---|---|
| `order.created` | `kidly.order.events` · `in/inventory/ProductOrderKafkaListener` | 재고 차감. 결과로 `stock.deducted` 또는 `stock.deduction-failed` |
| `order.cancelled` | 같은 리스너 | 차감한 재고 복구 |
| `order-item.cancelled` | 같은 리스너 | 판매자가 발송 전에 취소한 주문상품의 재고 복구(#693) |
| `seller.changed` | `kidly.seller.events` · `in/replica/ProductSellerKafkaListener` | 판매자 복사본(`replica_seller`) 갱신. 상세 · 목록의 상호명 |
| `product.changed`(자신) | Spring 이벤트 · `in/search/ProductSearchDocumentEventListener` | 검색용 표(`product_search_document`) 갱신 |

- Kafka 스위치가 꺼지면 같은 이벤트를 Spring 리스너(`Product*EventListener`, `@ConditionalOnProperty`)가 받는다

## Kafka 발행 (아웃박스, #551 · #565)
- 커밋 직전(`BEFORE_COMMIT`)에 상품이 발행한 이벤트(`producer() = product`)만 `product_outbox`에 저장한다. 업무와 같은 트랜잭션이라 업무가 롤백되면 메시지도 없고, 저장이 실패하면 업무도 롤백된다
- 릴레이(`ProductOutboxScheduler` → `ProductOutboxRelayService`)는 1초마다 id 순서로 100건을 보낸다. 실패하면 멈췄다가 다음 실행에 같은 메시지부터 보낸다. 매일 04:00(한국 시간)에 7일보다 먼저 보낸 메시지를 지운다
- 릴레이는 트랜잭션 없이 돈다. 브로커가 멈추면 메시지 하나에 최대 약 17초(`max.block.ms` 5초 + 응답 12초)를 기다리는데, 그동안 DB 연결을 쥐지 않기 위해서다
    - 결과 저장은 Spring Data `save`의 기본 트랜잭션에 기댄다. 직접 선언한 쿼리(`@Modifying` 조건부 UPDATE 등)로 바꾸면 트랜잭션 조각 빈(`app/outbox/support`)을 둔다
- 릴레이 잠금(advisory lock)은 없다. 앱 하나 · 스케줄러 스레드 하나라 겹치지 않는다. 인스턴스를 늘리면 더한다
- 스케줄러 스레드는 다른 도메인과 함께 쓰는 하나뿐이라, 브로커 장애 중에는 다른 스케줄러가 늦어질 수 있다
- 전송 실패 · 릴레이 · 정리 예외는 `ERROR`다. 한 건이 막히면 뒤 상품 이벤트가 모두 멈추고, Grafana "ERROR 로그" 알림은 ERROR 건수만 센다. 결제 아웃박스는 같은 경우를 `WARN`으로 남긴다(10-30 기준, 결제에 맞출지 물음)
    - 실패 로그는 실패 기록을 저장하기 전에 남긴다. DB도 함께 장애일 때 원인이 사라지지 않게 하기 위해서다
- `kidly.kafka.producer.enabled=false`면 스케줄러가 없고 저장만 한다. 테스트 설정은 릴레이 주기를 길게 두고 정리를 끄며, 테스트가 서비스를 직접 부른다
- 운영
    - 로그 `{env="dev"} |= "상품 아웃박스"`(보낸 메시지가 있는 실행만 `sent=N`). 지표는 `client_id=~"kidly-product-outbox-.*"`(Spring Kafka가 `-번호`를 붙인다)
    - 브로커 장애 동안 ERROR는 시간당 약 200 ~ 600줄, Slack은 처음 한 번 뒤 4시간마다다. 알림 규칙이 환경마다 하나라 그동안 다른 ERROR는 새 알림을 만들지 못한다

## Kafka 수신 (#692 · #693)
- 구독 그룹 `kidly.product`, 팩토리 `in/ProductKafkaListenerConfig`. 헤더 `eventType`으로 고르고, 받지 않는 종류는 건너뛰며, 헤더가 없으면 DLT로 보낸다
- 실패하면 1초 간격으로 3번 더 시도한 뒤 `{토픽}.product.dlt`(파티션 `-1`)로 보낸다. 읽지 못한 본문과 다시 해도 같은 예외(NPE · `IllegalArgumentException` · `IllegalStateException` · `BusinessException`)는 바로 보낸다
- 스위치 `kidly.product.kafka.consumer.enabled`(환경 변수 `PRODUCT_KAFKA_CONSUMER_ENABLED`, 기본 true, 테스트 false). true면 Kafka 리스너만, false면 Spring 리스너만 받는다. 시작 여부는 `spring.kafka.listener.auto-startup`이 아니라 리스너 애노테이션의 `autoStartup`이 정한다
    - false 동안 Kafka에 쌓인 메시지는 true로 올릴 때 처리된다. Spring 리스너가 이미 처리한 이벤트지만 결과가 1회분이라 안전하다
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

## 상품 정보 변경됨의 브랜드 · 설명 · 사이즈별 실측 (ProductChangedData, #610)
검색 색인과 추천 복사본이 상품 DB를 읽지 않고 이 이벤트로만 채우므로 `data`에 `brand` · `description` · `sizeSpecs`를 더했다.

- `sizeSpecs`는 실측 행(`size_spec`)마다 하나다(`size`, `minHeightCm`, `maxHeightCm`, `measurements`). 옵션 단위로 두면 색상만 다른 옵션마다 같은 실측이 반복되어 사이즈 단위로 둔다
- 사이즈 무관 상품도 FREE 행의 실측을 담는다(실측은 필수다, SIZE-05). 빈 목록은 실측 행이 없는 상품뿐이다(11-02 정석님)
- 새 칸이 없는 예전 메시지를 읽으면 `sizeSpecs`는 compact 생성자가 빈 목록으로 바꾸고, `brand` · `description`은 null이다(11-02 정석님)
- 설명이 본문에 실리므로 등록 · 수정 요청의 설명을 5,000자로 제한한다. 1MB를 넘는 설명 하나가 아웃박스 릴레이를 멈추고 뒤의 재고 이벤트까지 막을 수 있어서다(11-02 정석님)
- `shared`는 JDK만 써서 JSON 왕복은 앱 테스트에서 확인한다(`ProductSaleStartApiTest`의 아웃박스 본문, `ProductChangedEventJsonIntegrationTest`)

## 상품 정보 변경됨 다시 보내기 (ProductChangedRepublishService, #577)
검색 구독 그룹은 `latest`라 처음 붙기 전에 나간 메시지를 읽지 않는다. 관리자가 `POST /api/v1/admin/products/changed-events`로 판매를 시작한 상품마다 `product.changed`를 한 번씩 다시 보낸다. 검색 수신을 배포한 뒤와 검색 색인을 새로 만들 때 환경마다 한 번 부른다.

- 기존 `ProductChangedPublisher`로 만들어 토픽 · 키 · 형식이 다른 발행과 같다. `eventId`는 새로 만들고(아웃박스는 같은 `eventId`를 한 번만 보낸다), `occurredAt`은 다시 보낸 시각이다. 받는 쪽은 반영하지만 `data`가 지금 상품 그대로라 값은 바뀌지 않는다
- 전체를 한 트랜잭션에서 보낸다. 실패하면 아웃박스에 아무것도 남지 않으니 다시 부른다. 여러 번 불러도 받는 쪽 값은 같다
- 만든 이벤트를 조회하는 API가 없어 `Location` 없이 200 `{ "publishedCount": N }`이다
- 주의
    - 다시 보내는 도중 같은 상품이 바뀌는 경우는 고려하지 않았다(10-30 정석님). 읽은 뒤 커밋 전에 판매자 수정이 커밋되면, 늦은 시각을 단 옛 값이 다음 변경 때까지 남는다. 수정이 적은 시간에 부른다
    - 상품마다 옵션 · 정보 값 · 실측 · 재고를 따로 읽고, 커밋 뒤 요청 스레드에서 다른 도메인 리스너가 상품마다 돈다. 상품이 수백 개를 넘으면 나눠 보내기나 batch fetch를 검토한다. 504를 받아도 이미 커밋됐을 수 있고, 다시 불러도 해가 없다
    - 상품 하나의 데이터가 깨지면 매번 전체가 500이다. 그 상품을 고친 뒤 다시 부른다

## 검색 분리 (10-29)
- 순서: 상품 이벤트 Kafka 발행 → 검색 서비스가 `product.changed`로 색인 · 다시 보내기(#577) → 목록 경로를 검색 서비스로 전환 → 상품의 `search` 코드 삭제 → `product_search_document` 삭제 마이그레이션
- 표는 엔티티를 지운 뒤 Flyway로 지운다. `ddl-auto: validate`라 엔티티가 남은 채 표를 먼저 지우면 앱이 뜨지 않는다
