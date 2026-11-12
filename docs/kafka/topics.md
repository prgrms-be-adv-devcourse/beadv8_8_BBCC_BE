# 토픽 목록

| 토픽 | `eventType` | 키 | 보내는 도메인 | 받는 도메인 |
|---|---|---|---|---|
| `kidly.member.events` | `member.registered` | 회원 ID | member | notification, payment |
| | `member.suspended` | 회원 ID | | childprofile, notification |
| | `member.changed` | 회원 ID | | seller |
| | `member.withdrawn` | 회원 ID | | auth, childprofile, community(찜 정리 · 리뷰 닉네임 지움(탈퇴 표시)), member(배송지), notification, order(#1001: 결제대기 주문 취소 · 회원 장바구니 정리), payment, recommend(활동 기록 · 기준 옷 지움), seller(#1093: 판매자 신청 개인정보 비움) |
| `kidly.seller.events` | `seller.changed` | 판매자 ID | seller | member, notification, order, product, recommend, search |
| | `seller.application-rejected` | 신청 ID | | notification |
| `kidly.childprofile.events` | `child.changed` | 자녀 ID | childprofile | auth, order, recommend |
| | `child.growth-recorded` | 자녀 ID | | recommend |
| `kidly.auth.events` | `auth.tokens-revoked` | 토큰 주체 ID | auth | gateway |
| | `child-account.changed` | 자녀 계정 ID | | childprofile, notification, order |
| `kidly.product.events` | `product.changed`(data에 관리자 판매 중지 `stoppedByAdmin`, 판매 시작 시각 `saleStartedAt`, 카테고리 경로마다 보일 성별 `visibleGender`) | 상품 ID | product | community, notification, order, recommend, search |
| | `stock.deducted` · `stock.deduction-failed` | 주문 ID | | order |
| `kidly.order.events` | `order.created` | 주문 ID | order | product |
| | `order.payment-requested` | 주문 ID | | payment |
| | `order.paid` | 주문 ID | | notification |
| | `order.cancelled`(data `reason`: OUT_OF_STOCK · PAYMENT_FAILED · PAYMENT_EXPIRED · BUYER_CANCELLED, #1121 추가 값 SELLER_UNAVAILABLE = 판매자가 판매할 수 없게 돼 결제 전 주문 전체 취소. 결제 뒤 판매자 몫 취소로 주문 전체가 취소될 때는 `order.cancelled`를 보내지 않는다) | 주문 ID | | notification, payment, product |
| | `order-item.cancelled`(data `reason`: 판매자 취소 OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER, #1121 추가 값 SELLER_UNAVAILABLE = 판매자가 판매할 수 없게 돼 시스템이 배송 전 몫 취소) | 주문 ID | | notification, payment, product |
| | `order-item.purchase-confirmed`(data에 확정 방식 `confirmType` DIRECT · AUTO, #901) | 주문 ID | | community, notification, recommend, settlement |
| | `delivery.shipped` | 주문 ID | | notification |
| | `delivery.delivered`(#1061 추가 칸 `autoConfirmAt`: 자동 구매확정 예정 시각 = 주문상품 배송 완료 시각 + 자동 구매확정 기간(주문의 자동 확정과 같은 기준), `items`: 그 배송(판매자 몫)의 주문상품 `orderItemId` · `productId` · `quantity`(취소 제외). 없는 예전 메시지는 null) | 주문 ID | | notification |
| | `cart.item-added`(#998 추가 칸 `ownerType` · `ownerId`: 장바구니 주인 MEMBER · CHILD, 키 = 결제 회원(보호자) ID) | 회원 ID | | recommend |
| | `cart.item-removed`(사용자가 뺄 때만, #709. #998 추가 칸 `ownerType` · `ownerId`) | 회원 ID | | recommend |
| | `cart.item-purchased`(결제완료로 산 칸 정리, #998: `cartItemId` · `ownerType` · `ownerId` · `memberId`(줄의 결제 회원, 실제 결제자와 다를 수 있음) · `optionId` · `childId` · `quantity`(이번에 산 수량. 칸에서 그만큼 빠지고 0이 되면 칸이 지워진다) · `orderId` · `orderItemId` · `reservationId`(예약 칸이었으면)) | 회원 ID | | order(조르기 결제 완료, 자기 토픽 #999) |
| | `wish.sent` | 자녀 ID | | notification |
| | `wish.paid`(조르기 예약 칸이 결제됨, 조르기마다 1번. data `payerType` · `payerId` · `orderId`, #999) | 자녀 ID | | notification |
| | `wish.rejected` | 자녀 ID | | notification |
| | `wish.expired`(기한 안에 결제 · 거절이 없어 시스템이 만료, #1008. data는 wish.sent와 같은 모양) | 자녀 ID | | notification |
| | `wish.cancelled`(처리 전에 철회, #968. #999 추가 칸 `cancelReason` DIRECT · CHILD_DELETED · ACCOUNT_DELETED · SYSTEM, 없는 예전 메시지는 DIRECT) | 자녀 ID | | (아직 없음) |
| `kidly.payment.events` | `payment.completed` · `payment.failed` | 주문 ID | payment | order |
| | `payment.refunded` | 주문 ID | | notification |
| | `payment.payout-completed` · `payment.payout-failed` | 정산서 ID | | settlement, notification(payout-completed) |
| `kidly.settlement.events` | `settlement.payout-requested` | 정산서 ID | settlement | payment |
| `kidly.search.events` | `search.performed`(#1089 추가 칸 `childId` 고른 자녀, 없는 예전 메시지는 자녀 없음) | 회원 ID | search | recommend |
| | `search.result-clicked`(검색 결과 클릭, #1004. #1089 추가 칸 `childId`) | 회원 ID | | 없음(추천이 필요할 때 받는다) |
| `kidly.community.events` | `review.changed`(#1274: 작성 · 실제로 바뀐 수정 · 삭제 · 검사 결과 · 관리자 숨김 확정 · 다시 공개. data `reviewId` · `orderItemId` · `productId` · `rating` · `fit`(없으면 null) · `visible`(지금 공개인지, 삭제 · 숨김이면 false). 바뀐 것 없는 수정 · 같은 값 요청은 보내지 않는다) | 리뷰 ID | community | recommend(받을 예정) |
| | `favorite.added`(#1274: 새로 찜했을 때만. data `favoriteId` · `memberId` · `productId`) | 회원 ID | | recommend(받을 예정) |
| | `favorite.removed`(#1274: 실제로 지웠을 때만. data `favoriteId` · `memberId` · `productId` · `cause` USER · MEMBER_WITHDRAWN(회원 탈퇴로 지움)) | 회원 ID | | recommend(받을 예정) |

- `kidly.community.events`는 커밋된 뒤 아웃박스에서 보내는 최소 한 번 전달입니다. 같은 `eventId`가 두 번 갈 수 있고(보낸 뒤 아웃박스에서 지우기 전에 서버가 멈추면), 순서는 키 단위로 대체로 지키지만 커밋 순서와 아웃박스 id 순서가 다를 때 · 격리 뒤 수동으로 다시 보낼 때 바뀔 수 있습니다. 받는 쪽은 `eventId`로 중복을 거르고, 리뷰는 `reviewId`, 찜은 (`memberId`, `productId`)마다 `occurredAt`이 더 늦은 것만 반영합니다. 닉네임 · 리뷰 본문 · 사진 주소는 담지 않습니다
- 순서는 **같은 토픽 + 같은 키** 안에서만 보장됩니다. 한 주문의 이벤트(생성 → 결제 요청 → 취소 → 구매확정)는 모두 `kidly.order.events`에 주문 ID 키로 들어가므로 순서가 지켜집니다.
- member는 `seller.changed`를 회원 서비스에서 Kafka로 받습니다. 토큰 무효화는 auth가 `auth.tokens-revoked`를 보내고 Gateway가 받아 처리합니다.
- 받는 도메인은 11-04 코드의 `@KafkaListener` 기준입니다. 조르기 이벤트(`wish.*`)는 받는 도메인이 없어도 표에 둡니다(받는 곳이 없으면 "(아직 없음)").
- 주문은 자기 토픽 `kidly.order.events`를 구독 그룹 `kidly.order`로 읽습니다(#999, 조르기 결제 완료). 자기 토픽 구독은 허용입니다: 받은 메시지를 다시 넣지 않고, 실패는 주문 DLT `kidly.order.events.order.dlt`로 갑니다. 결제 정리 트랜잭션과 떨어져 있어 조르기 처리가 실패해도 결제는 되돌아가지 않습니다.
- `wish.approved` · `wish.item-added`는 #999에서 보내지 않습니다(승인 단계 · 공용 장바구니를 없앰, 받는 도메인 없음).
- `child.changed`는 관리자 `POST /api/v1/admin/children/changed-events`로 삭제되지 않은 자녀 전체를 지금 값으로 다시 보낼 수 있습니다(#1055). 형식 · 키는 같고 `eventId` · `occurredAt`만 새로 정합니다. 받는 도메인(auth · order · recommend)은 여러 번 받아도 결과가 같습니다.
- `child.growth-recorded`는 관리자 `POST /api/v1/admin/children/growth-recorded-events`로 삭제되지 않은 자녀의 성장 이력을 다시 보낼 수 있습니다(#1110). `eventId`만 새로 정하며 받는 쪽은 `growthRecordId`로 중복을 거릅니다.
- 새 이벤트를 만들거나 토픽, 키, 메시지 형식을 바꾸려면 팀에 먼저 묻습니다. 결정되면 이 표에 추가합니다.

---

## 알림용 이벤트 데이터 계약

- `member.suspended` (`MemberSuspendedEvent`): `memberId`, `reason`, `suspendedAt`. 키는 회원 ID. 관리자 정지 시 저장한 정지 시각과 `occurredAt`, `suspendedAt`이 같다. 기존 `auth.tokens-revoked`도 유지한다.
- `seller.application-rejected` (`SellerApplicationRejectedEvent`): `applicationId`, `memberId`(신청자), `reason`, `rejectedAt`. 키는 신청 ID. 저장한 결정 시각과 `occurredAt`, `rejectedAt`이 같다.
- 두 이벤트는 상태 변경 트랜잭션에서 발행하고 기존 릴레이로 DB 커밋 후 Kafka에 전송한다. 롤백 시 전송하지 않는다. 기존 비동기 직접 전송 방식이므로 장애 시 유실될 수 있다.
- `reason`은 대상 회원에게 안내할 사유다. 이벤트 본문과 사유를 로그에 남기지 않는다.
- 수신 도메인 `notification`은 위 이벤트로 정지 사유 · 거절 사유 메일을 보낸다.
- `payment.refunded` (`PaymentRefundedEvent`): `memberId`(환불받은 회원), `orderId`, `cancellationId`, `items`(`orderItemId` · `amount`), `amount`(총 환불 금액, 원). 키는 주문 ID. 판매자가 주문상품 일부를 취소해 환불하면(`order-item.cancelled`) `cancellationId`가 있고, 주문 전체가 취소돼 환불하면(`order.cancelled`) `null`이다. `items[].amount`의 합은 `amount`와 같다. `memberId`를 더하기 전의 메시지에는 없어 `null`로 읽힌다. 환불에 실패했거나 환불하지 않은 경우(회원 불일치, 이미 환불됨)에는 보내지 않는다.
- `payment.payout-completed` (`PaymentPayoutCompletedEvent`): `settlementId`, `sellerId`, `amount`(실제로 입금한 금액, 원). 키는 정산서 ID. 0원 정산도 `amount=0`으로 보낸다. 같은 정산서의 지급 요청이 다시 오면 입금하지 않고 처음 결과로 다시 보내며, 이때 `eventId`가 달라진다.
- 수신 도메인 `notification`은 환불 완료 메일을 `memberId`에게 보내고, `memberId`가 `null`인 예전 메시지는 받는 사람을 알 수 없어 보내지 않는다. 정산금 지급 메일은 판매자에게 보내되 0원 정산은 입금이 없어 보내지 않고, `eventId` 대신 `settlementId`로 한 통만 만든다.
- `member.registered` (`MemberRegisteredEvent`): `memberId`, `email`, `resent`. 기존 회원 이메일은 관리자 `POST /api/v1/admin/members/registered-events`로 채운다. 회원 서비스가 이메일이 있는 회원(탈퇴 회원 제외)마다 `resent=true`인 `member.registered`를 새 `eventId`로 다시 보낸다(토픽 보존 기간이 지나 재소비로는 채울 수 없다). `resent`를 더하기 전의 메시지에는 없어 `null`로 읽히고 새 가입으로 본다. 수신 도메인 `notification`은 `resent`면 이메일 복사본만 저장하고 환영 메일 · 알림함은 만들지 않는다. `payment`는 지갑이 이미 있으면 새로 만들지 않아 그대로다.
- `product.changed` (`ProductChangedEvent`)의 `stoppedByAdmin`: 관리자가 운영 정책에 따라 판매를 중지한 상품이면 `true`. 판매자가 스스로 중지한 상품은 `false`이고, 이 칸을 더하기 전의 메시지도 `false`로 읽힌다. 판매자가 이미 중지한 상품을 관리자가 중지하면 판매 상태가 그대로라 이벤트가 나가지 않는다.
- `product.changed` (`ProductChangedEvent`)의 `saleStartedAt`: 상품이 처음 판매중이 된 시각(한국 시간)이다. 판매중지 뒤 다시 판매해도 바뀌지 않는다. 이 칸이 생기기 전의 메시지와, 판매를 시작하지 않고 등록 취소한 상품은 `null`이다. 이 칸이 생기기 전에 판매를 시작한 상품은 실제 판매 시작 시각을 알 수 없어 상품을 만든 시각으로 채웠다.
- `product.changed` (`ProductChangedEvent`)의 `categoryPath[].visibleGender`: 그 카테고리를 보여 줄 자녀 성별(`ALL` · `GIRL`)이다. 관리자만 정하는 내부 값이고, `GIRL`이면 남아 자녀에게는 그 카테고리를 보여 주지 않는다. 이 칸을 더하기 전의 메시지는 `ALL`로 읽힌다. 관리자가 카테고리의 보일 성별을 바꿔도 `product.changed`가 나가지 않으므로, 받는 쪽 복사본은 상품의 다음 변경이나 관리자 다시 보내기(`POST /api/v1/admin/products/changed-events`) 때 맞춰진다.
- 수신 도메인 `notification`은 `product.changed`로 상품 이름 복사본에 판매 상태와 재고 있는 옵션을 저장하고, 복사본보다 늦은 변경에서 판매 중이던 상품을 관리자가 중지하면 판매자에게 메일 · 알림함을, 재고가 있던 옵션이 새로 품절되면 알림함만 남긴다. 처음 받은 상품은 알리지 않는다.
- `member.withdrawn` (`MemberWithdrawnEvent`): `memberId`. 수신 도메인 `notification`은 그 회원의 알림함 알림 · 이메일 복사본 · 푸시 FID를 지우고, 이메일 발송 기록은 상태 · 시각 · 유형만 남기고 받는 주소를 비운다. 아직 보내지 않은 메일은 보내지 않는다. 같은 메시지를 다시 받아도 결과가 같다.
