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

## 예치금 충전 (`app/ChargeService` · `app/ChargeConfirmService` · `app/ChargeCancelService`, #123)
- 요청 `POST /api/v1/wallets/me/charges` → PENDING, 토스 주문번호는 UUID, 30분 뒤 만료
- 승인 `POST /api/v1/wallets/me/charges/confirm`은 트랜잭션을 나눈다. 토스 호출 동안 DB 트랜잭션을 잡지 않는다
  1. `ChargeService.startConfirming`: 금액 대조 · 만료 확인 후 CONFIRMING 커밋
  2. `ChargeConfirmService`: 트랜잭션 밖에서 토스 승인. 멱등 키는 토스 주문번호
  3. 결과 반영

| 토스 결과 | 충전 | 지갑 · 원장 | 응답 |
|---|---|---|---|
| 승인 | DONE, paymentKey 암호화 저장 | 락으로 읽어 입금, 원장 `CHARGE`(ref_id = 충전 ID) | 200 |
| 거절 | FAILED, 토스 오류 코드 저장 | 그대로 | 400 `CHARGE_REJECTED`(detail은 토스 메시지) |
| 모름(시간 초과 · 5xx) | CONFIRMING 유지 | 그대로 | 202. 실패로 처리하지 않는다(돈이 빠져나갔을 수 있다) |

- 남의 지갑 충전은 없는 충전처럼 404 `CHARGE_NOT_FOUND`
- 충전 목록 `GET /api/v1/wallets/me/charges`(#186)는 상태와 상관없이 내 지갑의 충전을 모두 최신순(ID 내림차순)으로 준다. 상태 필터는 없다
- paymentKey는 `app.port.PaymentKeyCipher`(AES-256-GCM, `out.crypto`)로 암호화한다. 키는 환경 변수 `PAYMENT_KEY_ENCRYPTION_KEY`(Base64 32바이트)이고, 없으면 앱이 뜨지 않는다. 로컬 · 테스트는 프로필 설정의 개발용 키를 쓴다
- 통합 · 기능 테스트는 토스 대신 `FakePaymentGateway`(결제 테스트 폴더, paymentKey로 결과 선택)를 쓴다
- 끝나지 않은 충전은 `in.scheduler.ChargeScheduler`가 정리한다(#158). 주기 `kidly.payment.charge.reconcile-interval`(기본 1분)
  - 승인 중으로 `confirming-stale-after`(기본 5분) 넘게 남은 충전: 토스 조회로 승인 → 입금 / 승인 안 됨 → FAILED / 모름 → 다음 실행. 승인 금액이 다르면 반영하지 않고 ERROR
  - 취소 중으로 `cancelling-stale-after`(기본 5분) 넘게 남은 충전: 같은 멱등 키로 토스 취소를 다시 불러 아래 취소 표대로 반영 / 모름 → 다음 실행
  - 만료 시각이 지난 PENDING 충전: EXPIRED
  - 충전 상태를 바꾸는 곳(승인 시작 · 승인 반영 · 실패 · 만료 · 취소)은 충전을 비관적 락으로 읽는다. 사용자 승인과 스케줄러가 겹쳐도 늦은 쪽이 바뀐 상태를 보고 멈춰 한 번만 입금한다. 원장 `(type, ref_id)` UK가 마지막으로 막는다

### 충전 취소 (#192)
- `POST /api/v1/wallets/me/charges/{chargeId}/cancel`. 완료(DONE) 충전만 전액 취소한다. 충전액만큼 잔액이 있어야 하고, 모자라면 409 `CHARGE_CANCEL_BALANCE_SHORT`
- 승인처럼 트랜잭션을 나눈다
  1. `ChargeService.startCancel`: 충전 → 지갑 순서로 락을 잡고(승인 반영과 같은 순서) CANCELLING 커밋. 충전액을 이때 먼저 빼고 원장 `CHARGE_CANCEL`(ref_id = 충전 취소 ID)을 남긴다. 토스 호출 동안 그 돈으로 결제하지 못하게 하려는 것이다
  2. `ChargeCancelService`: 트랜잭션 밖에서 토스 취소. 멱등 키는 `charge-cancellation-{충전 취소 ID}`
  3. 결과 반영

| 토스 결과 | 충전 · 취소 요청 | 지갑 · 원장 | 응답 |
|---|---|---|---|
| 취소됨 | CANCELLED · COMPLETED | 그대로(이미 뺌) | 200 |
| 거절 | DONE으로 되돌림 · FAILED(토스 오류 코드) | 다시 입금, 원장 `CHARGE_CANCEL_REVERT`(ref_id = 충전 취소 ID) | 400 `CHARGE_CANCEL_REJECTED`(detail은 토스 메시지) |
| 모름(시간 초과 · 5xx) | CANCELLING 유지 | 그대로(뺀 상태) | 202, `balance` null |

- 거절된 충전은 DONE이라 다시 취소할 수 있다. 새 취소 요청을 만들어 멱등 키도 새로 쓴다
- 아직 없음(다음 이슈): 원장 조회 API, 부분 취소, 토스에서 직접 취소한 충전 맞추기
