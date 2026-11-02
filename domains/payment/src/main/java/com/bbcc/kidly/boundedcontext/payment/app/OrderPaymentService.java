package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PayOrderCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrderRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문을 예치금으로 결제하고 결과를 payment.completed · payment.failed로 알린다.
 * 주문당 결제는 한 번이다. 이미 결제가 있으면 건너뛰고, 동시에 받아 확인을 통과해도 {@code payment.order_id} UK가 막는다.
 * 잔액 부족만 실패 결제를 저장한다. 금액 불일치는 결제 대상이 아니라 ERROR 로그와 payment.failed만 남긴다.
 * 지갑이 없으면 잔액 0으로 만들어 결제한다(잔액 부족).
 * 결제 전에 취소된 주문(order.cancelled)이면 차감하지 않고 아무것도 발행하지 않는다(기획서 2.1 ④).
 * 탈퇴로 동결된 지갑이면 차감하지 않고 payment.failed(WALLET_NOT_FOUND)를 발행한다(#670). 결제는 저장하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final CancelledOrderRepository cancelledOrderRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void pay(PayOrderCommand command) {
        if (paymentRepository.existsByOrderId(command.orderId())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!command.amountMatchesItems()) {
            log.error("주문 결제 요청 금액이 주문상품 금액의 합과 다릅니다. orderId={}, amount={}, items={}",
                command.orderId(), command.amount(), command.items());
            publishFailed(command, null, PaymentFailureCode.AMOUNT_MISMATCH, now);
            return;
        }
        // 취소와 같은 회원 지갑 락을 잡은 뒤 취소 기록을 본다. 동시에 온 취소가 먼저 끝났으면 차감하지 않는다
        walletRepository.createIfAbsent(command.memberId(), now);
        Wallet wallet = walletRepository.findWithLockByMemberId(command.memberId()).orElseThrow();
        if (cancelledOrderRepository.existsByOrderId(command.orderId())) {
            log.warn("이미 취소된 주문의 결제 요청이라 차감하지 않습니다. orderId={}, memberId={}",
                command.orderId(), command.memberId());
            return;
        }
        if (wallet.isFrozen()) {
            // 동결 전용 실패 사유를 더하면 shared 계약에 enum 값이 늘어 받는 쪽 배포가 먼저 필요하다. 쓸 수 있는 지갑이 없다는 뜻으로 기존 값을 쓴다
            log.warn("탈퇴로 동결된 지갑이라 결제하지 않습니다. orderId={}, memberId={}", command.orderId(), command.memberId());
            publishFailed(command, null, PaymentFailureCode.WALLET_NOT_FOUND, now);
            return;
        }
        if (wallet.canWithdraw(command.amount())) {
            complete(command, wallet, now);
        } else {
            failForInsufficientBalance(command, wallet, now);
        }
    }

    private void complete(PayOrderCommand command, Wallet wallet, LocalDateTime now) {
        wallet.withdraw(command.amount());
        Payment payment = paymentRepository.save(Payment.complete(command.orderId(), wallet.getId(),
            command.memberId(),
            command.items().stream().map(item -> PaymentItem.of(item.orderItemId(), item.amount())).toList(),
            now));
        walletLedgerRepository.save(WalletLedger.record(
            wallet.getId(), WalletLedgerType.PAYMENT, payment.getAmount(), wallet.getBalance(), payment.getId()));
        PaymentActionLog.record(PaymentAction.ORDER_PAID,
            "memberId={} walletId={} orderId={} paymentId={} amount={} balance={}",
            payment.getMemberId(), wallet.getId(), payment.getOrderId(), payment.getId(), payment.getAmount(),
            wallet.getBalance());

        eventPublisher.publish(new PaymentCompletedEvent(UUID.randomUUID(), now, new PaymentCompletedEvent.Data(
            payment.getId(),
            payment.getOrderId(),
            payment.getMemberId(),
            payment.getAmount(),
            payment.getItems().stream()
                .map(item -> new PaymentCompletedEvent.Item(item.getOrderItemId(), item.getAmount()))
                .toList(),
            payment.getPaidAt())));
    }

    private void failForInsufficientBalance(PayOrderCommand command, Wallet wallet, LocalDateTime now) {
        Payment payment = paymentRepository.save(Payment.fail(command.orderId(), wallet.getId(), command.memberId(),
            command.amount(), PaymentFailureCode.INSUFFICIENT_BALANCE));
        publishFailed(command, payment.getId(), PaymentFailureCode.INSUFFICIENT_BALANCE, now);
    }

    /**
     * @param paymentId 저장한 실패 결제 ID. 저장하지 않은 실패면 null
     */
    private void publishFailed(PayOrderCommand command, Long paymentId, PaymentFailureCode reasonCode,
        LocalDateTime now) {
        PaymentActionLog.record(PaymentAction.ORDER_PAYMENT_FAILED,
            "memberId={} orderId={} paymentId={} amount={} reasonCode={}",
            command.memberId(), command.orderId(), paymentId, command.amount(), reasonCode);
        eventPublisher.publish(new PaymentFailedEvent(UUID.randomUUID(), now, new PaymentFailedEvent.Data(
            paymentId,
            command.orderId(),
            command.memberId(),
            command.amount(),
            reasonCode,
            now)));
    }
}
