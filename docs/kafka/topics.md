# 토픽 목록

| 토픽 | `eventType` | 키 | 보내는 도메인 | 받는 도메인 |
|---|---|---|---|---|
| `kidly.member.events` | `member.registered` | 회원 ID | member | notification, payment |
| | `member.suspended` | 회원 ID | | notification |
| | `member.changed` | 회원 ID | | seller |
| | `member.withdrawn` | 회원 ID | | auth, childprofile, member(배송지), payment |
| `kidly.seller.events` | `seller.changed` | 판매자 ID | seller | member, order, product, recommend, search |
| | `seller.application-rejected` | 신청 ID | | notification |
| `kidly.childprofile.events` | `child.changed` | 자녀 ID | childprofile | auth, order, recommend |
| `kidly.auth.events` | `auth.tokens-revoked` | 토큰 주체 ID | auth | gateway |
| `kidly.product.events` | `product.changed` | 상품 ID | product | community, order, recommend, search |
| | `stock.deducted` · `stock.deduction-failed` | 주문 ID | | order |
| `kidly.order.events` | `order.created` | 주문 ID | order | product |
| | `order.payment-requested` | 주문 ID | | payment |
| | `order.cancelled` | 주문 ID | | payment, product |
| | `order-item.cancelled` | 주문 ID | | payment, product |
| | `order-item.purchase-confirmed` | 주문 ID | | community, recommend, settlement |
| | `cart.item-added` | 회원 ID | | recommend |
| `kidly.payment.events` | `payment.completed` · `payment.failed` | 주문 ID | payment | order |
| | `payment.refunded` | 주문 ID | | notification |
| | `payment.payout-completed` · `payment.payout-failed` | 정산서 ID | | settlement, notification(payout-completed) |
| `kidly.settlement.events` | `settlement.payout-requested` | 정산서 ID | settlement | payment |
| `kidly.search.events` | `search.performed` | 회원 ID | search | recommend |

- 순서는 **같은 토픽 + 같은 키** 안에서만 보장됩니다. 한 주문의 이벤트(생성 → 결제 요청 → 취소 → 구매확정)는 모두 `kidly.order.events`에 주문 ID 키로 들어가므로 순서가 지켜집니다.
- member는 `seller.changed`를 회원 서비스에서 Kafka로 받습니다. 토큰 무효화는 auth가 `auth.tokens-revoked`를 보내고 Gateway가 받아 처리합니다.
- 받는 도메인은 11-04 코드의 `@KafkaListener` 기준입니다. 그 밖의 주문 이벤트(장바구니 빼기, 위시 관련)는 Kafka로 받는 도메인이 없어 표에 넣지 않았습니다.
- 새 이벤트를 만들거나 토픽, 키, 메시지 형식을 바꾸려면 팀에 먼저 묻습니다. 결정되면 이 표에 추가합니다.

---

## 알림용 이벤트 데이터 계약

- `member.suspended` (`MemberSuspendedEvent`): `memberId`, `reason`, `suspendedAt`. 키는 회원 ID. 관리자 정지 시 저장한 정지 시각과 `occurredAt`, `suspendedAt`이 같다. 기존 `auth.tokens-revoked`도 유지한다.
- `seller.application-rejected` (`SellerApplicationRejectedEvent`): `applicationId`, `memberId`(신청자), `reason`, `rejectedAt`. 키는 신청 ID. 저장한 결정 시각과 `occurredAt`, `rejectedAt`이 같다.
- 두 이벤트는 상태 변경 트랜잭션에서 발행하고 기존 릴레이로 DB 커밋 후 Kafka에 전송한다. 롤백 시 전송하지 않는다. 기존 비동기 직접 전송 방식이므로 장애 시 유실될 수 있다.
- `reason`은 대상 회원에게 안내할 사유다. 이벤트 본문과 사유를 로그에 남기지 않는다.
- 수신 도메인 `notification`은 위 이벤트로 정지 사유 · 거절 사유 메일을 보낸다.
- 기존 회원 이메일은 Kafka에 보관된 `member.registered`를 알림 도메인이 재소비해 채운다. `member.registered` 데이터(`memberId`, `email`)는 유지하며 `resent` 추가나 회원 도메인의 재발행은 하지 않는다.
