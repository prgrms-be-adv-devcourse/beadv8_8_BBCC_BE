package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.OrderItemCancelCommand;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Refund;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundFailureCode;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundItem;
import com.bbcc.kidly.boundedcontext.payment.domain.RefundRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerType;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.payment.event.PaymentRefundedEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 발송 전에 취소한 주문상품(order-item.cancelled)의 금액을 구매자 예치금으로 환불한다(#711).
 * 결제 요청 · 주문 취소와 같은 회원 지갑 락을 먼저 잡고, 취소(cancellationId)마다 환불 기록을 하나만 남긴다(refund.cancellation_id UK).
 * 같은 취소가 다시 오면 기록이 있어 건너뛴다. 환불할 수 없으면 입금 없이 실패한 환불로 남긴다.
 * 환불하면 환불 이벤트(payment.refunded)를 발행한다. 실패한 환불은 발행하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderItemCancellationService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final WalletLedgerRepository walletLedgerRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void handle(OrderItemCancelCommand command) {
        LocalDateTime now = LocalDateTime.now();
        // 지갑이 없어도 만든 뒤 락을 잡아, 같은 회원의 결제 · 취소와 순서대로 처리한다
        walletRepository.createIfAbsent(command.memberId(), now);
        Optional<Wallet> wallet = walletRepository.findWithLockByMemberId(command.memberId());
        if (refundRepository.existsByCancellationId(command.cancellationId())) {
            return;
        }
        // 주문은 결제완료 주문만 판매자 취소를 받고, 같은 키(주문 ID)라 결제 완료 뒤에 온다. 없으면 다시 해도 같다(재시도 없이 DLT)
        Payment payment = paymentRepository.findByOrderId(command.orderId())
            .orElseThrow(() -> new IllegalStateException("부분 취소된 주문의 결제가 없습니다"));
        if (!payment.getMemberId().equals(command.memberId())
            || wallet.filter(found -> found.getId().equals(payment.getWalletId())).isEmpty()) {
            log.error("부분 취소된 주문의 회원과 결제한 회원 · 지갑이 달라 환불하지 않습니다. orderId={}, cancellationId={}, "
                + "memberId={}, paymentId={}", command.orderId(), command.cancellationId(), command.memberId(),
                payment.getId());
            return;
        }
        Map<Long, Long> amounts = command.items().stream().collect(Collectors.toMap(
            OrderItemCancelCommand.Item::orderItemId, OrderItemCancelCommand.Item::amount, Long::sum,
            LinkedHashMap::new));
        Optional<RefundFailureCode> failure = payment.checkRefundable(amounts);
        if (failure.isPresent()) {
            fail(command, payment, amounts, failure.get());
            return;
        }
        refund(command, payment, wallet.get(), amounts, now);
    }

    private void refund(OrderItemCancelCommand command, Payment payment, Wallet wallet, Map<Long, Long> amounts,
        LocalDateTime now) {
        List<RefundItem> items = amounts.entrySet().stream()
            .map(amount -> {
                PaymentItem item = payment.refund(amount.getKey(), amount.getValue());
                return RefundItem.of(item.getId(), amount.getValue());
            })
            .toList();
        Refund refund = refundRepository.save(
            Refund.complete(payment.getId(), payment.getOrderId(), command.cancellationId(), items, now));
        wallet.deposit(refund.getAmount());
        walletLedgerRepository.save(WalletLedger.record(
            wallet.getId(), WalletLedgerType.REFUND, refund.getAmount(), wallet.getBalance(), refund.getId()));
        PaymentActionLog.record(PaymentAction.ORDER_ITEM_REFUNDED,
            "memberId={} walletId={} orderId={} cancellationId={} paymentId={} refundId={} amount={} balance={} "
                + "reason={}",
            command.memberId(), wallet.getId(), command.orderId(), command.cancellationId(), payment.getId(),
            refund.getId(), refund.getAmount(), wallet.getBalance(), command.reason());
        List<PaymentRefundedEvent.Item> refundedItems = amounts.entrySet().stream()
            .map(amount -> new PaymentRefundedEvent.Item(amount.getKey(), amount.getValue()))
            .toList();
        eventPublisher.publish(new PaymentRefundedEvent(UUID.randomUUID(), now, new PaymentRefundedEvent.Data(
            refund.getId(), payment.getId(), payment.getOrderId(), command.cancellationId(), refundedItems,
            refund.getAmount(), refund.getRefundedAt(), command.memberId())));
    }

    private void fail(OrderItemCancelCommand command, Payment payment, Map<Long, Long> amounts,
        RefundFailureCode failureCode) {
        long amount = amounts.values().stream().mapToLong(Long::longValue).sum();
        Refund refund = refundRepository.save(
            Refund.fail(payment.getId(), payment.getOrderId(), command.cancellationId(), amount, failureCode));
        log.error("부분 취소를 환불하지 못해 실패한 환불로 남깁니다. orderId={}, cancellationId={}, paymentId={}, "
            + "refundId={}, failureCode={}", command.orderId(), command.cancellationId(), payment.getId(),
            refund.getId(), failureCode);
    }
}
