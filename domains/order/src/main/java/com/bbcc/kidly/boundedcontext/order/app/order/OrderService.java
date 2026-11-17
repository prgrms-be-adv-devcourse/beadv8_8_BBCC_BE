package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.LostPaymentRecheck;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries.CancelledDelivery;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries.Delivery;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.TransitionResult;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderPaidEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 요청 · 결제 반영 · 취소. 주문하기는 OrderPlacementService, 구매확정은 OrderConfirmationService(#1508, 하는 일별로 분리),
 * 조회는 OrderQueryService가 담당한다.
 * 로그: 상태가 바뀌면 INFO, 받은 요청을 무시하면 DEBUG(결과 · 현재 상태), 받은 이벤트의 주문이 없으면 WARN.
 * 개인 정보(배송지 · 연락처 · 자녀 이름 · 키 · 몸무게)는 남기지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDeliveries orderDeliveries;
    private final OrderStatusHistoryRepository historyRepository;
    private final WaitingRecordCloser waitingRecordCloser;
    private final OrderQueryService orderQueries;
    private final OrderEventSupport events;

    /**
     * 재고 차감이 끝난 주문의 결제를 요청한다. 주문상품별 금액을 함께 보낸다(결제가 주문상품 단위로 결제 · 환불).
     * 주문이 없거나 이미 요청했거나 결제대기가 아니면 아무것도 하지 않는다.
     */
    public void requestPayment(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = events.findForEvent(orderId, "stock.deducted");
        if (order == null) {
            return;
        }
        TransitionResult result = order.requestPayment(now);
        if (result != TransitionResult.APPLIED) {
            logIgnored("stock.deducted", order, result);
            return;
        }
        // 주문 UPDATE 실패가 커밋 때가 아니라 여기서 나야 리스너가 잡아 이벤트 처리 실패 기록으로 보관한다(관리자 재처리 대상, #449)
        orderRepository.flush();
        events.publish(new OrderPaymentRequestedEvent(UUID.randomUUID(), now,
            new OrderPaymentRequestedEvent.Data(order.getId(), order.getMemberId(), order.getTotalAmount(),
                order.getItems().stream()
                    .map(item -> new OrderPaymentRequestedEvent.Item(item.getId(), item.getAmount()))
                    .toList())));
        log.info("결제 요청: orderId={} amount={}", order.getId(), order.getTotalAmount());
    }

    /**
     * 결제완료로 바꾼다. 결제 시각은 이벤트 값이 아니라 지금 시각을 쓴다.
     * 결제대기면 주문이 회원 · 주문상품 구성 · 금액을 대조하고, 다르면 PaymentMismatchException이 그대로 나간다.
     * 취소된 주문에 온 결제 완료는 반영하지 않는다. 환불은 결제 쪽이 처리하므로 환불 여부 확인만 WARN으로 알린다
     * (#290, 올 때마다). 단 결제완료 뒤 구매자가 취소한 주문에 같은 결제 ID로 다시 온 결제 완료는 이미 반영한 중복이라
     * DEBUG로만 남긴다(#354-1, 그 결제의 환불은 취소 때 order.cancelled로 이미 요청됨).
     * 실제로 결제완료로 바뀌면 order.paid를 같은 트랜잭션에서 낸다(#899).
     *
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false
     */
    public boolean markPaid(MarkPaidCommand command) {
        return markPaid(command, OrderActor.event(OrderActor.PAYMENT_COMPLETED));
    }

    /**
     * 이벤트가 아닌 주체(관리자 재처리)가 결제완료로 바꾼다. 상태 이력에 그 주체를 남긴다(#442).
     */
    public boolean markPaid(MarkPaidCommand command, OrderActor actor) {
        LocalDateTime now = LocalDateTime.now();
        Order order = events.findForEvent(command.orderId(), "payment.completed");
        if (order == null) {
            return false;
        }
        Order.Status from = order.getStatus();
        TransitionResult result = order.markPaid(command.paymentId(), command.memberId(), command.amount(),
            toPaidItems(command.items()), now);
        if (order.isPaidAfterCancel(command.paymentId())) {
            warnPaidAfterCancel(command, order);
            return false;
        }
        if (result != TransitionResult.APPLIED) {
            logIgnored("payment.completed", order, result);
            return false;
        }
        historyRepository.save(actor.historyWithoutReason(order.getId(), OrderStatusHistory.TargetType.ORDER,
            order.getId(), from, order.getStatus(), now));
        publishPaid(order, now);
        log.info("결제완료: orderId={} paymentId={}", order.getId(), command.paymentId());
        return true;
    }

    /**
     * 결제완료로 실제로 바뀐 때 한 번(#899). 상품 정보는 주문할 때 저장한 스냅샷이고, 배송지 · 자녀 정보는 넣지 않는다.
     * 같은 트랜잭션에서 배송 생성 · 장바구니 정리가 실패해 되돌려지면 아웃박스 행도 남지 않는다.
     */
    private void publishPaid(Order order, LocalDateTime now) {
        List<OrderPaidEvent.Item> items = order.getItems().stream().map(OrderService::paidItem).toList();
        events.publish(new OrderPaidEvent(UUID.randomUUID(), now, new OrderPaidEvent.Data(order.getId(),
            order.getMemberId(), order.getPaidAt(), order.getTotalAmount(), items)));
    }

    // 상품 스냅샷이 없는 옛 주문상품은 상품 칸이 비어 있다(주문 타임라인과 같은 처리)
    private static OrderPaidEvent.Item paidItem(OrderItem item) {
        OrderItem.ProductSnapshot product = item.getProduct();
        return new OrderPaidEvent.Item(item.getId(),
            product == null ? null : product.sellerId(),
            product == null ? null : product.productId(),
            product == null ? null : product.productName(),
            product == null ? null : product.optionName(),
            item.getQuantity(), item.getAmount());
    }

    /**
     * 결제 완료 반영이 동시 변경에 져 되돌려질 때 주문이 지금 어떤지 본다. 진 쪽이 읽어 둔 주문은 낡았고 그 트랜잭션은
     * 되돌려지므로 새 트랜잭션에서 다시 읽는다. 이긴 쪽이 주문을 취소했으면 취소된 주문에 온 결제 완료와 같으므로 환불 확인
     * WARN을 남긴다(#519). 아직 결제대기면 반영이 통째로 되돌려진 것이라(예: 장바구니 줄 동시 변경) 다시 처리해야 한다(#594).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public LostPaymentRecheck recheckLostPaymentCompleted(MarkPaidCommand command) {
        Order order = orderRepository.findById(command.orderId()).orElse(null);
        if (order != null && order.getStatus() == Order.Status.PENDING_PAYMENT) {
            return LostPaymentRecheck.STILL_PENDING;
        }
        if (order == null || !order.isPaidAfterCancel(command.paymentId())) {
            return LostPaymentRecheck.SETTLED;
        }
        warnPaidAfterCancel(command, order);
        return LostPaymentRecheck.PAID_AFTER_CANCEL;
    }

    private static void warnPaidAfterCancel(MarkPaidCommand command, Order order) {
        log.warn("취소된 주문에 결제 완료 — 결제 쪽이 환불 처리, 환불 여부 확인 필요: event=payment.completed "
                + "reason=paid-after-cancel "
                + "orderId={} paymentId={} amount={} cancelReason={}",
            command.orderId(), command.paymentId(), command.amount(), order.getCancelReason());
    }

    /**
     * 비어 있으면(null) 빈 목록으로 넘겨 주문이 구성 불일치로 판단하게 한다. 빈 칸(null)도 그대로 넘긴다.
     */
    private static List<Order.PaidItem> toPaidItems(List<MarkPaidCommand.Item> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .map(item -> item == null ? null : new Order.PaidItem(item.orderItemId(), item.amount()))
            .toList();
    }

    /**
     * 결제 실패로 취소한다. 결제대기가 아니면 그대로 둔다.
     */
    public void cancelForPaymentFailure(Long orderId) {
        cancel(orderId, "payment.failed", OrderActor.event(OrderActor.PAYMENT_FAILED), null,
            order -> order.cancel(Order.CancelReason.PAYMENT_FAILED));
    }

    /**
     * 이벤트가 아닌 주체(관리자 결제 반영 취소 · 이벤트 처리 실패 재처리)가 결제 실패 사유로 취소한다. 상태 이력에 그 주체를 남긴다(#442).
     *
     * @param handled 그 주체가 지금 다루는 재처리 대기 기록. 주문 취소가 대기 기록을 닫을 때 빼고 그 주체가 결과를 정한다(#598)
     */
    public void cancelForPaymentFailure(Long orderId, OrderActor actor, WaitingRecord handled) {
        cancel(orderId, "payment.failed", actor, handled, order -> order.cancel(Order.CancelReason.PAYMENT_FAILED));
    }

    /**
     * 재고 차감 실패로 취소하고 주문상품별 사유를 남긴다. 결제대기가 아니면 그대로 둔다(사유도 남기지 않음).
     * 주문에 없는 주문상품 ID는 주문이 무시하므로 여기서 WARN만 남긴다.
     */
    public void cancelForStockDeductionFailure(StockDeductionFailedCommand command) {
        cancelForStockDeductionFailure(command, OrderActor.event(OrderActor.STOCK_DEDUCTION_FAILED), null);
    }

    /**
     * 이벤트가 아닌 주체(관리자 이벤트 처리 실패 재처리, #449)가 재고 차감 실패로 취소한다. 상태 이력에 그 주체를 남긴다.
     *
     * @param handled 그 주체가 지금 다루는 재처리 대기 기록(없으면 null). 주문 취소가 대기 기록을 닫을 때 뺀다(#598)
     */
    public void cancelForStockDeductionFailure(StockDeductionFailedCommand command, OrderActor actor,
        WaitingRecord handled) {
        Map<Long, String> reasons = command.items().stream()
            .collect(Collectors.toMap(StockDeductionFailedCommand.Item::orderItemId,
                StockDeductionFailedCommand.Item::reason, (first, last) -> last));
        cancel(command.orderId(), "stock.deduction-failed", actor, handled,
            order -> {
            warnUnknownOrderItems(command.orderId(), order, reasons);
            return order.cancelForOutOfStock(reasons);
        });
    }

    private static void warnUnknownOrderItems(Long orderId, Order order, Map<Long, String> reasons) {
        List<Long> orderItemIds = order.getItems().stream().map(OrderItem::getId).toList();
        reasons.keySet().stream()
            .filter(orderItemId -> !orderItemIds.contains(orderItemId))
            .forEach(orderItemId -> log.warn("재고 차감 실패 주문상품 없음: event=stock.deduction-failed orderId={} "
                + "orderItemId={} reason=unknown-order-item", orderId, orderItemId));
    }

    /**
     * 결제 마감 만료로 취소한다. 마감이 지났는지는 주문이 직접 확인한다(마감 전이면 그대로).
     * 결제대기가 아니면 그대로 둔다(그 사이 결제완료됐으면 결제완료가 이긴다).
     */
    public void cancelForPaymentExpiry(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        cancel(orderId, "payment-expiry", OrderActor.system(OrderActor.PAYMENT_EXPIRY), null,
            order -> order.expirePayment(now));
    }

    /**
     * 결제대기이고 결제 마감이 now(포함) 이전인 주문 ID. afterId 다음부터 주문 ID 오름차순 limit건.
     * now는 스케줄러가 한 실행에 한 번 정해 모든 배치에 같은 값을 넘긴다.
     */
    @Transactional(readOnly = true)
    public List<Long> findOverduePaymentOrderIds(LocalDateTime now, Long afterId, int limit) {
        return orderRepository.findOverduePaymentOrderIds(Order.Status.PENDING_PAYMENT, now, afterId, Limit.of(limit));
    }

    /**
     * 구매자가 주문을 전체 취소한다(#354). 결제대기 주문, 또는 판매자별 배송이 모두 발송 전인 결제완료 주문만.
     * 주문 취소 · 판매자 배송 취소 · order.cancelled 발행을 한 트랜잭션으로 한다(결제 환불 · 재고 복구는 이벤트를 받은 쪽).
     * 판매자 발송과 동시에 오면 배송 @Version으로 한쪽만 성공한다(진 쪽은 공통 409, 다시 누르면 아래 상태 검사로 409).
     * 이미 취소된 주문은(사유 무관) 그대로 상세를 돌려준다. 남의 주문은 없는 주문으로 본다.
     * 배송 완료 · 구매확정은 배송이 발송된 뒤에만 일어나므로 발송 검사가 그 경합도 막는다.
     */
    public OrderDetail cancelByBuyer(Long memberId, Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        List<Delivery> deliveries = orderDeliveries.findByOrder(orderId);
        Order.Status from = order.getStatus();
        TransitionResult result = order.cancelByBuyer(deliveries.stream().anyMatch(Delivery::shipped));
        if (result == TransitionResult.NOT_APPLICABLE) {
            throw new BusinessException(OrderErrorCode.CANCEL_NOT_ALLOWED_SHIPPED);
        }
        if (result == TransitionResult.APPLIED) {
            OrderActor actor = OrderActor.buyer(memberId, OrderActor.BUYER_CANCEL);
            String reason = order.getCancelReason().name();
            historyRepository.save(actor.history(orderId, OrderStatusHistory.TargetType.ORDER, orderId, from,
                order.getStatus(), reason, now));
            List<CancelledDelivery> changes = orderDeliveries.cancelByOrder(orderId);
            for (CancelledDelivery change : changes) {
                historyRepository.save(OrderStatusHistory.of(orderId, OrderStatusHistory.TargetType.DELIVERY,
                    change.delivery().deliveryId(), change.from(), change.delivery().status(), reason, actor.type(),
                    actor.id(), actor.source(), now));
            }
            deliveries = changes.stream().map(CancelledDelivery::delivery).toList();
            publishCancelled(order, actor, null, now);
        } else {
            logIgnored("cancel-by-buyer", order, result);
        }
        return OrderDetail.from(order, deliveries, orderQueries.sellerNamesOf(List.of(order)));
    }

    /**
     * 회원 탈퇴(#1001): 그 회원의 결제대기 주문을 구매자 취소와 같은 전이(사유 BUYER_CANCELLED)로 취소하고 order.cancelled를 낸다.
     * 주체는 SYSTEM · 출처 member.withdrawn(상태 이력). 결제된 주문 · 배송 중 주문은 건드리지 않는다. 이미 취소된 주문은 대상이 아니므로
     * 같은 이벤트가 여러 번 와도 결과 · 이벤트는 한 번분이다.
     *
     * @return 이번에 취소한 주문 수
     */
    public int cancelPendingForMemberWithdrawal(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        OrderActor actor = OrderActor.system(OrderActor.MEMBER_WITHDRAWN);
        int cancelled = 0;
        for (Order order : orderRepository.findAllByMemberIdAndStatusOrderByIdAsc(memberId,
            Order.Status.PENDING_PAYMENT)) {
            Order.Status from = order.getStatus();
            TransitionResult result = order.cancelByBuyer(false);
            if (result != TransitionResult.APPLIED) {
                logIgnored("member-withdrawn", order, result);
                continue;
            }
            String reason = order.getCancelReason().name();
            historyRepository.save(actor.history(order.getId(), OrderStatusHistory.TargetType.ORDER, order.getId(),
                from, order.getStatus(), reason, now));
            for (CancelledDelivery change : orderDeliveries.cancelByOrder(order.getId())) {
                historyRepository.save(OrderStatusHistory.of(order.getId(), OrderStatusHistory.TargetType.DELIVERY,
                    change.delivery().deliveryId(), change.from(), change.delivery().status(), reason, actor.type(),
                    actor.id(), actor.source(), now));
            }
            publishCancelled(order, actor, null, now);
            cancelled++;
        }
        return cancelled;
    }

    /**
     * 판매자가 판매할 수 없게 된 주문 하나 정리(#1121, 한 주문 = 한 트랜잭션). 결제대기면 주문 전체 취소(사유 SELLER_UNAVAILABLE,
     * order.cancelled), 결제완료면 그 판매자 배송이 발송 전일 때만 판매자 발송 전 취소와 같은 경로로 그 판매자 몫 취소
     * (order-item.cancelled). 배송 중 · 배송 완료 · 이미 취소 · 그 밖의 상태는 그대로다. 주체는 SYSTEM · 출처 seller.changed.
     */
    public void cancelForUnavailableSeller(Long orderId, Long sellerId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        if (order.getStatus() == Order.Status.PENDING_PAYMENT) {
            // 상태는 취소 경로가 주문을 다시 읽어 도메인이 판단하고, 그 사이 결제 · 만료와 겹치면 주문 @Version으로 한쪽만 성공한다
            cancel(orderId, OrderActor.SELLER_CHANGED, OrderActor.system(OrderActor.SELLER_CHANGED), null,
                Order::cancelForSellerUnavailable);
        } else if (order.getStatus() == Order.Status.PAID) {
            orderDeliveries.cancelForUnavailableSeller(orderId, sellerId);
        }
    }

    /**
     * 주문이 없으면 무시. 이번에 취소했을 때만 order.cancelled를 발행한다(사유는 주문에 기록된 값,
     * 주문상품 순서 그대로).
     *
     * @param source 취소를 일으킨 이벤트 · 작업 이름(로그용)
     * @param actor 상태 이력에 남길 주체
     * @param handled 취소하는 주체가 지금 다루는 재처리 대기 기록(없으면 null)
     * @param change 주문을 취소하는 도메인 메서드. 이번에 취소했으면 APPLIED
     */
    private void cancel(Long orderId, String source, OrderActor actor, WaitingRecord handled,
        Function<Order, TransitionResult> change) {
        LocalDateTime now = LocalDateTime.now();
        Order order = events.findForEvent(orderId, source);
        if (order == null) {
            return;
        }
        Order.Status from = order.getStatus();
        TransitionResult result = change.apply(order);
        if (result != TransitionResult.APPLIED) {
            logIgnored(source, order, result);
            return;
        }
        // 주문 UPDATE 실패가 커밋 때가 아니라 여기서 나야 리스너가 잡아 이벤트 처리 실패 기록으로 보관한다(관리자 재처리 대상, #449)
        orderRepository.flush();
        historyRepository.save(actor.history(orderId, OrderStatusHistory.TargetType.ORDER, orderId, from,
            order.getStatus(), order.getCancelReason().name(), now));
        publishCancelled(order, actor, handled, now);
    }

    /**
     * 주문을 이번에 취소했을 때 모두 여기로 온다(취소 지점 한 곳): order.cancelled를 주문상품 순서대로 발행하고 결과 한 줄을 남기며,
     * 그 주문의 재처리 대기 기록을 같은 트랜잭션에서 닫는다(#598, handled는 빼고).
     */
    private void publishCancelled(Order order, OrderActor actor, WaitingRecord handled, LocalDateTime now) {
        events.publish(new OrderCancelledEvent(UUID.randomUUID(), now,
            new OrderCancelledEvent.Data(order.getId(), order.getMemberId(), order.getCancelReason().name(),
                order.getItems().stream()
                    .map(item -> new OrderCancelledEvent.Item(
                        item.getId(), item.getProduct().optionId(), item.getQuantity()))
                    .toList())));
        WaitingRecordCloser.Closed closed = waitingRecordCloser.closeFor(order.getId(), actor, handled, now);
        // 결과 하나에 한 줄: 닫은 대기 기록이 있으면 그 ID를 같은 줄에 붙인다
        if (closed.isEmpty()) {
            log.info("주문 취소: orderId={} reason={}", order.getId(), order.getCancelReason());
        } else {
            log.info("주문 취소: orderId={} reason={} closedEventFailureIds={} closedPaymentRecoveryId={}", order.getId(),
                order.getCancelReason(), closed.eventFailureIds(), closed.paymentRecoveryId());
        }
    }

    private void logIgnored(String event, Order order, TransitionResult result) {
        log.debug("이벤트 무시: event={} orderId={} result={} status={}",
            event, order.getId(), result, order.getStatus());
    }
}
