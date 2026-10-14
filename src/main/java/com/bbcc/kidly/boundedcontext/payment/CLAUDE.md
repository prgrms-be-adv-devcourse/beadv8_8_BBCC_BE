# 결제 도메인

결제 관련 규칙 · 결정은 루트가 아니라 이 파일에 둔다.

## 주문 결제 (`app/OrderPaymentService`, #105)
- `order.payment-requested`를 받아 예치금으로 결제하고 `payment.completed` · `payment.failed`를 발행한다
- 주문당 결제는 한 번이다. 그 주문의 결제가 이미 있으면 건너뛰고, 동시에 와도 `payment.order_id` UK가 막는다
- 잔액을 바꿀 때는 `WalletRepository.findWithLockByMemberId`(비관적 락)로 지갑을 읽는다
- 결과별 처리

| 경우 | 결제 저장 | 잔액 · 원장 | 발행 | 로그 |
|---|---|---|---|---|
| 잔액 충분 | `COMPLETED` | 차감, 원장 `PAYMENT`(ref_id = 결제 ID) | `payment.completed` | - |
| 잔액 부족 | `FAILED`(`INSUFFICIENT_BALANCE`) | 그대로 | `payment.failed` | - |
| 요청 총액 ≠ 주문상품 금액 합 | 저장 안 함 | 그대로 | `payment.failed`(`AMOUNT_MISMATCH`, `paymentId` null) | ERROR |
| 지갑 없음 | 저장 안 함 | 그대로 | `payment.failed`(`WALLET_NOT_FOUND`, `paymentId` null) | ERROR |

- 실패한 주문은 다시 결제하지 않는다. 충전한 뒤에도 같은 주문은 무시하고, 새 주문으로 결제한다
  - 멱등 확인은 상태와 상관없이 그 주문의 결제가 있는지만 본다. `FAILED` 결제도 다시 결제를 막는다
  - 주문 쪽도 `payment.failed`를 받으면 주문을 취소한다(#103). 다시 결제를 허용하려면 주문 쪽과 계약부터 다시 정한다
- `payment.wallet_id`는 NOT NULL을 유지한다. 지갑 없는 실패를 저장하려고 null을 허용하지 않는다
- 저장하지 않은 실패는 멱등 확인에 걸리지 않아, 같은 이벤트가 다시 오면 `payment.failed`도 다시 발행된다. 받는 쪽은 한 번만 반영한다
