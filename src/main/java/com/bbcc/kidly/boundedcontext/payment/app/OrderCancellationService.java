package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrder;
import com.bbcc.kidly.boundedcontext.payment.domain.CancelledOrderRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.Refund;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundItem;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 전체가 취소되면(order.cancelled) 결제된 주문은 예치금을 환불하고, 결제 전이면 취소된 주문으로 기록해
 * 그 뒤에 온 결제 요청을 차감하지 않게 한다(기획서 2.1 ④). 환불 이벤트는 기획서에 없어 발행하지 않는다.
 * 결제 요청과 같은 회원 지갑 락을 먼저 잡아, 결제 · 취소가 동시에 와도 늦은 쪽이 앞의 결과를 보고 처리한다.
 * 같은 취소가 다시 오면 결제가 이미 환불(REFUNDED)됐거나 취소 기록이 있어 건너뛴다.
 * 부르는 쪽 트랜잭션과 따로 커밋 · 롤백한다. 실패하면 부르는 쪽이 다시 처리할 취소로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final CancelledOrderRepository cancelledOrderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OrderCancelCommand command) {
        LocalDateTime now = LocalDateTime.now();
        // 지갑이 없어도 만든 뒤 락을 잡아, 동시에 온 결제 요청과 순서대로 처리한다
        walletRepository.createIfAbsent(command.memberId(), now);
        Optional<Wallet> wallet = walletRepository.findWithLockByMemberId(command.memberId());
        paymentRepository.findByOrderId(command.orderId()).ifPresentOrElse(
            payment -> refund(command, payment, wallet, now),
            () -> rememberCancelled(command, now));
    }

    private void refund(OrderCancelCommand command, Payment payment, Optional<Wallet> wallet, LocalDateTime now) {
        if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            return;
        }
        if (!payment.getMemberId().equals(command.memberId())
            || wallet.filter(found -> found.getId().equals(payment.getWalletId())).isEmpty()) {
            log.error("취소된 주문의 회원과 결제한 회원 · 지갑이 달라 환불하지 않습니다. orderId={}, memberId={}, paymentId={}",
                command.orderId(), command.memberId(), payment.getId());
            return;
        }
        List<RefundItem> items = payment.getItems().stream()
            .filter(item -> item.getAmount() > item.getRefundedAmount())
            .map(item -> {
                long left = item.getAmount() - item.getRefundedAmount();
                payment.refund(item.getOrderItemId(), left);
                return RefundItem.of(item.getId(), left);
            })
            .toList();
        Refund refund = refundRepository.save(
            Refund.completeForOrderCancellation(payment.getId(), payment.getOrderId(), items, now));
        Wallet found = wallet.get();
        found.deposit(refund.getAmount());
        walletLedgerRepository.save(WalletLedger.record(
            found.getId(), WalletLedgerType.REFUND, refund.getAmount(), found.getBalance(), refund.getId()));
        log.info("취소된 주문의 결제를 환불했습니다. orderId={}, paymentId={}, amount={}, reason={}",
            command.orderId(), payment.getId(), refund.getAmount(), command.reason());
    }

    private void rememberCancelled(OrderCancelCommand command, LocalDateTime now) {
        if (cancelledOrderRepository.existsByOrderId(command.orderId())) {
            return;
        }
        cancelledOrderRepository.save(CancelledOrder.of(command.orderId(), command.memberId(), command.reason(), now));
    }
}
