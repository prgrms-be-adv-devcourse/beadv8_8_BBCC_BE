# 결제 도메인

결제 관련 규칙 · 결정은 루트가 아니라 이 파일에 둔다.

## 주문 결제 (`app/OrderPaymentService`, #105)
- `order.payment-requested`를 받아 예치금으로 결제하고 `payment.completed` · `payment.failed`를 발행한다
- 주문당 결제는 한 번이다. 그 주문의 결제가 이미 있으면 건너뛰고, 동시에 와도 `payment.order_id` UK가 막는다
- 잔액을 바꿀 때는 `WalletRepository.findWithLockByMemberId`(비관적 락)로 지갑을 읽는다
- 지갑은 가입(`member.registered`) 때 만들지만, 그 리스너가 실패해도 메워지도록 지갑을 쓰는 곳(충전 요청 · 결제 · 환불 · 정산 입금)은 `createIfAbsent`(`ON CONFLICT DO NOTHING`)로 없으면 잔액 0으로 만든 뒤 락으로 읽는다(#349). 조회(잔액 · 원장)는 만들지 않고 잔액 0 · 빈 목록을 준다
- 결과별 처리

| 경우 | 결제 저장 | 잔액 · 원장 | 발행 | 로그 |
|---|---|---|---|---|
| 잔액 충분 | `COMPLETED` | 차감, 원장 `PAYMENT`(ref_id = 결제 ID) | `payment.completed` | - |
| 잔액 부족 | `FAILED`(`INSUFFICIENT_BALANCE`) | 그대로 | `payment.failed` | - |
| 요청 총액 ≠ 주문상품 금액 합 | 저장 안 함 | 그대로 | `payment.failed`(`AMOUNT_MISMATCH`, `paymentId` null) | ERROR |

- 지갑이 없으면 잔액 0으로 만들어 결제하므로 잔액 부족이다. `WALLET_NOT_FOUND`는 계약에 남아 있지만 보내지 않는다

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
| 승인(토스 결제 상태 `DONE`) | DONE, paymentKey 암호화 저장 | 락으로 읽어 입금, 원장 `CHARGE`(ref_id = 충전 ID) | 200 |
| 거절 | FAILED, 토스 오류 코드 저장(`failure_code` 100자, 넘으면 잘라 저장) | 그대로 | 400 `CHARGE_REJECTED`(detail은 토스 메시지) |
| 모름(시간 초과 · 5xx · 승인 응답이 `DONE`이 아님) | CONFIRMING 유지 | 그대로 | 202. 실패로 처리하지 않는다(돈이 빠져나갔을 수 있다). 입금 대기(`WAITING_FOR_DEPOSIT`) 같은 응답은 스케줄러 조회가 `DONE`일 때 입금한다(#485) |

- 남의 지갑 충전은 없는 충전처럼 404 `CHARGE_NOT_FOUND`
- 결제창이 실패 · 취소로 끝나면(failUrl) `POST /api/v1/wallets/me/charges/fail`(`orderId`, `code`)로 PENDING 충전을 바로 FAILED로 바꾼다(#437). 토스 오류 코드는 `failure_code`에 남긴다. 이미 FAILED면 그대로 200, 그 밖의 상태는 409. 실패로 바꾼 충전은 승인할 수 없다
  - 결제창이 아예 돌아오지 않는 경우(카카오페이 자동 충전 뒤 등)는 화면이 충전 목록의 PENDING · `expiresAt`으로 안내하고, 사용자가 그만두면 같은 API를 부른다
- 충전 목록 `GET /api/v1/wallets/me/charges`(#186)는 상태와 상관없이 내 지갑의 충전을 모두 최신순(ID 내림차순)으로 준다. 상태 필터는 없다
- paymentKey는 `app.port.PaymentKeyCipher`(AES-256-GCM, `out.crypto`)로 암호화한다. 키는 환경 변수 `PAYMENT_KEY_ENCRYPTION_KEY`(Base64 32바이트)이고, 없으면 앱이 뜨지 않는다. 로컬 · 테스트는 프로필 설정의 개발용 키를 쓴다
- 통합 · 기능 테스트는 토스 대신 `FakePaymentGateway`(결제 테스트 폴더, paymentKey로 결과 선택)를 쓴다
- 끝나지 않은 충전은 `in.scheduler.ChargeScheduler`가 정리한다(#158). 주기 `kidly.payment.charge.reconcile-interval`(기본 1분)
  - 승인 중으로 `confirming-stale-after`(기본 5분) 넘게 남은 충전: 토스 조회로 승인 → 입금 / 승인 안 됨 → FAILED / 모름 → 다음 실행. 승인 금액이 다르면 반영하지 않고 ERROR
    - 그사이 토스에서 부분 취소됐으면(`PARTIAL_CANCELED`) 반영하지 않고 승인 중으로 둔 채 ERROR를 남긴다(#485). 실패로 바꾸면 남은 결제액이 입금되지 않는다. 부분 취소는 운영자가 처리한다(웹훅과 같다)
  - 취소 중으로 `cancelling-stale-after`(기본 5분) 넘게 남은 충전: 같은 멱등 키로 토스 취소를 다시 불러 아래 취소 표대로 반영 / 모름 → 다음 실행
  - 만료 시각이 지난 PENDING 충전: EXPIRED
  - 충전 상태를 바꾸는 곳(승인 시작 · 승인 반영 · 실패 · 만료 · 취소)은 충전을 비관적 락으로 읽는다. 사용자 승인과 스케줄러가 겹쳐도 늦은 쪽이 바뀐 상태를 보고 멈춰 한 번만 입금한다. 원장 `(type, ref_id)` UK가 마지막으로 막는다

### 주문 취소 환불 (`app/OrderCancellationService`, #269)
- `order.cancelled`를 받는다(기획서 2.1 ④: 결제 완료와 결제 제한 시간 만료가 겹쳐 만료가 이긴 경우). 환불 이벤트는 기획서에 없어 발행하지 않는다
- 결제 요청과 같은 회원 지갑 락을 먼저 잡고 그 주문의 결제를 본다. 결제 · 취소가 동시에 와도 늦은 쪽이 앞의 결과를 본다

| 그 주문의 결제 | 처리 |
|---|---|
| COMPLETED · PARTIALLY_REFUNDED | 남은 금액을 모두 지갑에 입금, 원장 `REFUND`(ref_id = 환불 ID), 환불 기록(`cancellation_id` null), 결제 REFUNDED |
| 없음 | 취소된 주문 기록(`cancelled_order`, `order_id` UK). 그 뒤 온 `order.payment-requested`는 차감 · 발행 없이 건너뜀(WARN) |
| FAILED · REFUNDED | 아무것도 안 함 (같은 취소가 다시 와도 한 번만 환불) |
| 회원 · 지갑이 결제와 다름 | 환불 안 함, ERROR 로그 |

- 처리 실패 대비(#350): 받은 이벤트를 다시 받지 못할 수 있어서 결제 안에서 메운다
  - `handle`은 받는 처리와 다른 트랜잭션(`REQUIRES_NEW`)이다. 실패하면 처리한 것만 롤백되고, `app/PaymentEventHandler`가 `order_cancellation_retry`(`order_id` UK)에 남긴다(ERROR 로그). 남기면 처리 완료이고, 남기기까지 실패하면 예외가 나가 Kafka가 재시도 뒤 DLT로 보낸다
  - `OrderCancellationRetryScheduler`가 주기(`kidly.payment.order-cancellation.retry-interval`, 기본 1분)마다 다시 처리한다. 성공하면 지우고, 실패하면 시도 횟수 · 마지막 오류를 남긴다. 횟수 제한은 없다
  - 위 표대로 같은 취소는 한 번만 반영되므로 여러 번 다시 처리해도 된다

## 충전 취소 (#192)
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
- 토스가 `ALREADY_CANCELED_PAYMENT`로 거절하면(토스에서 이미 취소됨) 취소 완료로 반영한다(#207)
- 아직 없음(다음 이슈): 부분 취소

### 토스에서 직접 취소한 충전 맞추기 (#207)
- 토스 웹훅 `POST /api/v1/webhooks/toss`(`@PublicApi`). 토스 상점관리자에 이 URL을 등록해야 동작한다(배포 주소가 정해지면 등록)
- `PAYMENT_STATUS_CHANGED` 중 상태가 `CANCELED` · `PARTIAL_CANCELED`인 것만 본다. 나머지 이벤트 · 우리 충전이 아닌 주문번호 · 완료(DONE)가 아닌 충전은 200으로 넘어간다
- 토스 웹훅에는 서명이 없어 본문은 믿지 않는다. 주문번호로 토스를 다시 조회(`PaymentGateway.findCancellationByOrderId`)해 확정한다

| 토스 조회 | 충전 | 지갑 · 원장 | 응답 |
|---|---|---|---|
| 전액 취소됨 | CANCELLED, 완료된 취소 요청(사유 `토스에서 직접 취소`) | 잔액 범위에서 충전액을 빼고 원장 `CHARGE_CANCEL`(ref_id = 충전 취소 ID). 못 뺀 금액은 ERROR 로그(운영자 처리) | 200 |
| 부분 취소됨 | 그대로 | 그대로, ERROR 로그 | 200 |
| 취소 안 됨 | 그대로 | 그대로 | 200 |
| 모름 | 그대로 | 그대로 | 503 `PG_RESULT_UNKNOWN`(토스가 웹훅을 다시 보낸다) |

- 반영은 충전 → 지갑 순서로 락을 잡고, 그사이 완료가 아니게 된 충전은 건너뛴다. 웹훅이 두 번 와도 한 번만 뺀다
- 잔액은 0 밑으로 내려가지 않는다(`ck_wallet_balance`). 미수금은 따로 기록하지 않는다
- 아직 없음: 부분 취소 반영, 웹훅 유실 대비 주기 조회

## 입출금 내역 (`app/WalletService`, #200)
- `GET /api/v1/wallets/me/ledgers`: 내 지갑의 원장을 최신순(ID 내림차순)으로 준다. `amount`는 입금 양수 · 출금 음수, `refId`는 `type`에 따라 충전 · 충전 취소 · 결제 · 환불 · 정산 ID
- 지갑이 없으면 빈 목록이다(잔액 조회는 0). 종류 · 기간 필터는 없다

## 관리자 결제 조회 (`app/PaymentQueryService`, #312)
- `GET /api/v1/admin/payments`: 모든 회원의 결제를 최신순(ID 내림차순)으로 준다. `/api/v1/admin/**`라 ADMIN만 호출한다
- 한 줄은 내 결제 내역(`GET /api/v1/payments`)과 같고 `memberId`를 더한다. 환불은 `status` · `refundedAmount`로 보인다. 회원 · 상태 · 기간 필터는 없다

## 정산 지급 (`app/SettlementPayoutService`, #210)
- `settlement.payout-requested`를 받아 판매자(`sellerId` = 회원 ID) 예치금에 정산금액을 입금하고 `payment.payout-completed` · `payment.payout-failed`를 발행한다. 계약은 #209, 정산서 식별자는 정산과 맞춰 `settlementId`
- 정산서마다 지급 기록(`payout`, `settlement_id` UK)을 남겨 한 번만 입금한다. 기록이 있는 정산서로 요청이 다시 오면 입금 없이 저장된 결과를 다시 발행한다(기획서 2.1 ⑦). 받는 쪽(정산)은 같은 결과를 한 번만 반영한다
- 지갑을 락으로 읽은 뒤 지급 기록을 본다. 같은 요청이 동시에 와도 한 번만 입금하고, 지급 기록 UK와 원장 `(type, ref_id)` UK가 마지막으로 막는다

| 경우 | 지갑 · 원장 · 지급 기록 | 발행 |
|---|---|---|
| 입금 | 입금, 원장 `SETTLEMENT`(ref_id = 정산서 ID), 기록 COMPLETED | `payment.payout-completed`(ledgerId, paidAt) |
| 0원 | 입금 · 원장 없이 기록 COMPLETED | `payment.payout-completed`(ledgerId null, paidAt) |
| 이미 처리한 정산서 | 그대로 | 기록대로 완료 · 실패를 다시 발행 |

- `paidAt`은 결제가 지급 완료로 처리한 시각이다. 0원도 같다
- 판매자 지갑이 없으면 잔액 0으로 만들어 입금한다. 그래서 지금은 지급 실패가 생기지 않는다. `WALLET_NOT_FOUND`는 계약과 이전 기록에 남아 있다
- 지급 실패는 다시 시도하지 않는다. 탈퇴 회원 처리는 탈퇴 정책과 같이 정한다
- 도메인 간 이벤트는 스프링 이벤트라 리스너 실패 · 서버 중단 때 유실될 수 있다. 결과 유실은 정산이 같은 요청을 다시 보내면 복구된다. 요청 자체의 유실 복구와 재전송 장치는 추후 보완

## 행동 로그 (`app/PaymentActionLog` · `app/PaymentAction`)
- 예치금 · 충전 · 결제 상태가 바뀐 일을 로거 `action.payment`에 INFO 한 줄로 남긴다. 조회는 남기지 않는다(요청 로그 `RequestLoggingFilter`가 있다)
- `PaymentActionLog.record(PaymentAction.X, "memberId={} amount={}", memberId, amount)`. 한 줄은 `주문 결제 완료 action=ORDER_PAID memberId=1 amount=15000`처럼 한글 설명(`PaymentAction.description`) · action 뒤에 key=value를 잇는다. 사람은 한글로 읽고, 검색 · 파싱은 영문 key=value로 한다
- 트랜잭션 안에서 부르면 커밋된 뒤에 남긴다. 롤백된 일은 남기지 않는다
  - 바깥 트랜잭션의 커밋 뒤 콜백(`afterCommit`, 일반 `@TransactionalEventListener`) 안에서 새 트랜잭션 없이 부르면 남지 않는다. 결제 서비스는 자기 트랜잭션 안에서 부른다
- `action.payment`의 INFO를 끄면 아무것도 하지 않는다
- 운영 파일 로그(`ASYNC_FILE`)는 큐가 80% 넘게 차면 INFO를 버린다(logback 기본값). 금액의 기준은 원장(`wallet_ledger`)이고, 행동 로그는 추적용이다
- 필드 이름: `memberId` · `walletId` · 대상 ID(`chargeId` · `cancellationId` · `orderId` · `paymentId` · `refundId` · `settlementId` · `ledgerId`) · `amount` · `balance`(바뀐 뒤 잔액) · `failureCode` / `reasonCode`

| action | 남기는 곳 | 필드 |
|---|---|---|
| `CHARGE_REQUESTED` | `ChargeService.request` | memberId, walletId, chargeId, amount |
| `CHARGE_CONFIRM_STARTED` | `ChargeService.startConfirming` (CONFIRMING 커밋) | memberId, walletId, chargeId, amount |
| `CHARGE_CONFIRMED` | `ChargeService.approve` (사용자 승인 · 스케줄러 조회) | memberId, walletId, chargeId, amount, balance |
| `CHARGE_FAILED` | `ChargeService.fail` · `abandon`(결제창 실패, 이미 실패면 남기지 않음) | walletId, chargeId, failureCode |
| `CHARGE_EXPIRED` | `ChargeReconcileService.expire` | walletId, chargeId |
| `CHARGE_CANCEL_REQUESTED` | `ChargeService.startCancel` (충전액 먼저 뺌) | memberId, walletId, chargeId, cancellationId, amount, balance |
| `CHARGE_CANCELLED` | `ChargeService.completeCancel` | memberId, walletId, chargeId, amount, balance |
| `CHARGE_CANCEL_FAILED` | `ChargeService.failCancel` (다시 입금) | memberId, walletId, chargeId, cancellationId, failureCode, amount, balance |
| `CHARGE_CANCELLED_EXTERNALLY` | `ChargeService.applyExternalCancel` | memberId, walletId, chargeId, amount, recoveredAmount, shortfallAmount, balance |
| `ORDER_PAID` | `OrderPaymentService` | memberId, walletId, orderId, paymentId, amount, balance |
| `ORDER_PAYMENT_FAILED` | `OrderPaymentService` (잔액 부족 · 금액 불일치) | memberId, orderId, paymentId(불일치면 null), amount, reasonCode |
| `ORDER_CANCEL_RECORDED` | `OrderCancellationService` (결제 전 취소, `cancelled_order` 저장) | memberId, orderId, reason |
| `ORDER_REFUNDED` | `OrderCancellationService` | memberId, walletId, orderId, paymentId, refundId, amount, balance, reason |
| `PAYOUT_COMPLETED` | `SettlementPayoutService` (처음 처리한 정산서만, 0원 포함) | memberId(판매자), walletId, settlementId, amount, ledgerId, balance |

## Kafka 수신 (#593)
- 결제가 받는 이벤트 중 보내는 쪽이 Kafka로 보내는 3종을 Kafka로 받는다. 구독 그룹 `kidly.payment`, 토픽마다 리스너 하나(`in/event`)

| 토픽 | 이벤트 | 리스너 | 처리(`app/PaymentEventHandler`) | DLT |
|---|---|---|---|---|
| `kidly.member.events` | `member.registered` | `PaymentMemberEventListener` | 지갑 만들기(`createIfAbsent`) | `kidly.member.events.payment.dlt` |
| `kidly.order.events` | `order.payment-requested` · `order.cancelled` | `PaymentOrderEventListener` | 결제 · 환불(결제 전 취소 기록) | `kidly.order.events.payment.dlt` |

- 정산 `settlement.payout-requested`는 정산이 Kafka로 보내지 않아 Spring 리스너(`app/SettlementPayoutRequestedListener`)로만 받는다
- 처리는 `app/PaymentEventHandler` 한 곳에 있고 Kafka 리스너 · Spring 리스너가 같은 처리를 부른다. 같은 이벤트를 다시 받아도 결과는 1회분이다(지갑은 없을 때만, 주문당 결제 1건, 같은 취소는 한 번만 환불)
- 헤더 `eventType`으로 고르고 받지 않는 종류(회원 변경 · 탈퇴, 주문 생성 · 구매확정)는 본문을 읽지 않고 건너뛴다. 모르는 필드는 무시한다
- 실패 처리(`in/event/PaymentKafkaListenerConfig`): 한 건씩 처리 완료를 커밋하고, 실패하면 1초 간격으로 3번 더 시도한 뒤 받은 토픽의 DLT 파티션 0으로 보낸다
  - 재시도 없이 바로 DLT: 읽지 못한 본문(`JacksonException`), `BusinessException`, `IllegalArgumentException`(`eventType` 헤더 없음 포함), `IllegalStateException`, NPE. 다시 해도 같아서다
  - DLT 로그에는 토픽 · `eventType` · `eventId` · 키 · 예외 종류만 남긴다(본문에 이메일 · 금액이 있다)
- 읽기 시작 위치는 결제 팩토리에서 `earliest`(앱 기본 `latest`를 덮어씀). 처음 Kafka로 바꿀 때 빠지는 메시지가 없게 한다. 이미 Spring으로 처리한 메시지를 다시 읽어도 결과가 1회분이라 안전하다
- 스위치 `kidly.payment.kafka.consumer.enabled`(기본 true, 환경 변수 `PAYMENT_KAFKA_CONSUMER_ENABLED`)
  - true: Kafka 리스너만 받는다. 같은 이벤트의 Spring 리스너(`app/MemberRegisteredListener` · `OrderPaymentRequestedListener` · `OrderCancelledListener`)는 뜨지 않는다
  - false: Kafka 리스너를 시작하지 않고 Spring 리스너로 받는다(장애 때 되돌리는 용도). 같은 이벤트를 양쪽에서 받지 않는다
  - 테스트 설정은 false라 다른 도메인 테스트(주문 흐름 등)는 Spring 경로로 돈다. Kafka 수신은 `PaymentKafkaReceiveIntegrationTest`가 그 안에서 결제 리스너를 켜서 본다
- 로컬에서 결제가 받으려면 Kafka가 떠 있어야 한다(`docker compose up -d`에 포함)

## Kafka 발행 (아웃박스, #534 · #550)
- 결제가 발행한 이벤트(`payment.completed` · `payment.failed` · `payment.payout-completed` · `payment.payout-failed`)를 토픽 `kidly.payment.events`로도 보낸다. 규칙은 팀 「Kafka 토픽 규칙」을 따른다
- 키 = `correlationId`(결제는 주문 ID, 정산 지급은 정산서 ID), 헤더 = `eventType` · `eventId`, 본문 = 공통 칸 7개 + `data`(JSON). `__TypeId__`는 붙이지 않는다
- 받는 쪽(주문 · 정산)은 아직 Spring 이벤트(`@DomainEventListener`)로 받는다. Kafka로 옮기는 건 받는 도메인이 하고, 그때 이미 쌓인 메시지는 처리된 것이므로 첫 배포에서 오프셋을 끝으로 옮긴다
- **저장** (`app/PaymentOutboxRecorder`): 커밋 직전(`BEFORE_COMMIT`)에 결제가 발행한 이벤트(`producer() = payment`)만 같은 트랜잭션에서 `payment.payment_outbox`에 저장한다. 결제 서비스의 발행 코드는 그대로다
  - 업무가 롤백되면 메시지도 남지 않고, 저장이 실패하면 업무도 롤백된다. 트랜잭션 밖에서 발행한 이벤트는 저장되지 않는다(`@DomainEventListener`와 같은 조건)
  - 같은 이벤트(같은 `eventId`)를 다시 발행하면 메시지를 하나 더 만들지 않는다(`ON CONFLICT (event_id) DO NOTHING`, 동시에 와도 업무를 실패시키지 않는다)
  - 저장은 Kafka 보내기가 꺼져 있어도(`kidly.kafka.producer.enabled=false`) 한다
- **릴레이** (`app/PaymentOutboxRelayService`, `in.scheduler.PaymentOutboxRelayScheduler`): 주기(`kidly.payment.outbox.relay-interval`, 기본 1초)마다 안 보낸 메시지를 `id` 순서로 `batch-size`(기본 100)건 보내고 `sent_at`을 기록한다
  - 한 트랜잭션에서 PostgreSQL advisory lock(`pg_try_advisory_xact_lock`)을 얻은 쪽만 보낸다. 서버가 여러 대여도 한 번에 하나만 돌아 순서가 지켜진다. 못 얻으면 아무것도 하지 않는다
  - 보내다 실패하면 그 메시지에 `attempt_count` · `last_error`를 남기고 멈춘다. 다음 주기에 같은 메시지부터 다시 보낸다(순서 유지). 횟수 제한은 없다
  - 실패는 업무를 실패시키지 않고 본문 없는 WARN(`eventType` · `eventId` · `key` · 원인)만 남긴다
  - 메시지는 **한 번 이상** 갈 수 있다(보낸 뒤 커밋 전에 죽으면 다시 보낸다). 받는 쪽은 `eventId`나 업무 상태로 거른다
  - `kidly.kafka.producer.enabled`가 false면 릴레이 스케줄러를 만들지 않는다
- **정리** (`app/PaymentOutboxCleanupService`, `in.scheduler.PaymentOutboxCleanupScheduler`): 매일 04:00(KST, `cleanup-cron`)에 보낸 지 `retention`(기본 7일, 브로커 보존 기간과 같음)이 지난 메시지를 지운다. 안 보낸 메시지는 지우지 않는다
- **전송** (`out.kafka.KafkaPaymentEventSender`): 저장된 토픽 · 키 · 본문 그대로 보내고 브로커 응답을 기다린다(동기). 받았다고 답하지 않으면 `PaymentEventSendException`
- 설정은 결제 안(`out.kafka.PaymentKafkaConfig`)에만 둔다: 토픽 선언(파티션 3 · 복제 1, 파티션 수는 바꾸지 않는다)과 결제 전용 템플릿. 템플릿은 Boot 프로듀서 설정(주소)에 직렬화 · `acks=all` · `enable.idempotence=true` · `max.block.ms`(3초) · `request.timeout.ms`(5초) · `delivery.timeout.ms`(10초) · `linger.ms`(0)를 덮어쓰고 본문을 찍는 기본 실패 로그를 끈다. 빈으로 두지 않아 앱 `spring.kafka`와 Boot 기본 `KafkaTemplate`은 그대로다
  - 릴레이는 스케줄러 스레드(앱 전체 1개) · DB 트랜잭션을 쥔 채 기다리므로, 브로커가 죽어 있으면 한 주기가 최대 약 13초 걸려 그만큼 다른 스케줄러가 늦어질 수 있다
- 로컬 `bootRun`은 `docker compose up -d`로 Kafka를 함께 띄운다. 없으면 메시지가 아웃박스에 쌓이고 릴레이가 주기마다 WARN을 남긴다. Kafka를 띄우면 쌓인 것부터 순서대로 보낸다
