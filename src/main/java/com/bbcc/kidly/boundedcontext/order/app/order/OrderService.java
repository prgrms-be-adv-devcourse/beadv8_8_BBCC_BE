package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.TransitionResult;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문하기 · 결제 요청 · 주문 조회. 남의 주문은 없는 주문으로 본다.
 * 로그: 상태가 바뀌면 INFO, 받은 요청을 무시하면 DEBUG(결과 · 현재 상태), 받은 이벤트의 주문이 없으면 WARN.
 * 개인 정보(배송지 · 연락처 · 자녀 이름 · 키 · 몸무게)는 남기지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private static final int AUTO_CONFIRM_BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final ProductReplicaRepository productReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;
    private final DeliveryRepository deliveryRepository;
    private final EventPublisher eventPublisher;
    private final Order.Policy orderPolicy;

    /**
     * 복사본이 없는 옵션 · 자녀는 여기서 404. 나머지 검사(빈 항목 · 중복 · 수량 · 가격 · 판매 중 · 자기 상품 · 자녀)는
     * Order.place가 한다. 저장 뒤 order.created를 발행한다.
     */
    public OrderDetail place(Long memberId, PlaceOrderCommand command) {
        LocalDateTime now = LocalDateTime.now();
        List<Order.RequestedItem> requested = command.items().stream().map(this::toRequestedItem).toList();
        PlaceOrderCommand.ShippingAddress address = command.shippingAddress();

        Order order = orderRepository.save(Order.place(memberId, requested,
            new Order.ShippingAddress(address.recipientName(), address.phone(), address.zipCode(), address.address()),
            orderPolicy, now));

        eventPublisher.publish(new OrderCreatedEvent(UUID.randomUUID(), now, new OrderCreatedEvent.Data(
            order.getId(),
            order.getMemberId(),
            order.getItems().stream()
                .map(item -> new OrderCreatedEvent.Item(
                    item.getId(), item.getProduct().optionId(), item.getQuantity()))
                .toList())));
        log.info("주문 생성: orderId={} itemCount={} totalAmount={}",
            order.getId(), order.getItems().size(), order.getTotalAmount());
        return OrderDetail.from(order, List.of());
    }

    /**
     * 재고 차감이 끝난 주문의 결제를 요청한다. 주문상품별 금액을 함께 보낸다(결제가 주문상품 단위로 결제 · 환불).
     * 주문이 없거나 이미 요청했거나 결제대기가 아니면 아무것도 하지 않는다.
     */
    public void requestPayment(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(orderId, "stock.deducted");
        if (order == null) {
            return;
        }
        TransitionResult result = order.requestPayment(now);
        if (result != TransitionResult.APPLIED) {
            logIgnored("stock.deducted", order, result);
            return;
        }
        eventPublisher.publish(new OrderPaymentRequestedEvent(UUID.randomUUID(), now,
            new OrderPaymentRequestedEvent.Data(order.getId(), order.getMemberId(), order.getTotalAmount(),
                order.getItems().stream()
                    .map(item -> new OrderPaymentRequestedEvent.Item(item.getId(), item.getAmount()))
                    .toList())));
        log.info("결제 요청: orderId={} amount={}", order.getId(), order.getTotalAmount());
    }

    /**
     * 결제완료로 바꾼다. 결제 시각은 이벤트 값이 아니라 지금 시각을 쓴다.
     * 결제대기면 주문이 회원 · 주문상품 구성 · 금액을 대조하고, 다르면 PaymentMismatchException이 그대로 나간다.
     *
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false
     */
    public boolean markPaid(MarkPaidCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(command.orderId(), "payment.completed");
        if (order == null) {
            return false;
        }
        TransitionResult result = order.markPaid(command.paymentId(), command.memberId(), command.amount(),
            toPaidItems(command.items()), now);
        if (result != TransitionResult.APPLIED) {
            logIgnored("payment.completed", order, result);
            return false;
        }
        log.info("결제완료: orderId={} paymentId={}", order.getId(), command.paymentId());
        return true;
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
        cancel(orderId, "payment.failed", order -> order.cancel(Order.CancelReason.PAYMENT_FAILED));
    }

    /**
     * 결제 마감 만료로 취소한다. 마감이 지났는지는 주문이 직접 확인한다(마감 전이면 그대로).
     * 결제대기가 아니면 그대로 둔다(그 사이 결제완료됐으면 결제완료가 이긴다).
     */
    public void cancelForPaymentExpiry(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        cancel(orderId, "payment-expiry", order -> order.expirePayment(now));
    }

    /**
     * 결제대기이고 결제 마감이 지난 주문 ID. 주문 ID 오름차순 100건.
     */
    @Transactional(readOnly = true)
    public List<Long> findOverduePaymentOrderIds() {
        return orderRepository.findTop100ByStatusAndPaymentDueAtLessThanEqualOrderByIdAsc(
                Order.Status.PENDING_PAYMENT, LocalDateTime.now())
            .stream()
            .map(Order::getId)
            .toList();
    }

    /**
     * 구매자가 직접 구매확정한다. 없거나 남의 주문이면 404. 이미 확정이어도 주문상품을 돌려준다.
     */
    public OrderDetail.Item confirmByBuyer(Long memberId, Long orderId, Long orderItemId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        confirm(order, orderItemId, "confirm-by-buyer", target -> target.confirmItem(orderItemId, now));
        return OrderDetail.Item.from(findItem(order, orderItemId));
    }

    /**
     * 자동 구매확정. 기간이 지났는지는 주문이 직접 확인한다(기간 전 · 이미 확정이면 그대로). 주문이 없으면 무시.
     */
    public void confirmAutomatically(Long orderId, Long orderItemId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(orderId, "auto-confirm");
        if (order == null) {
            return;
        }
        confirm(order, orderItemId, "auto-confirm", target -> target.autoConfirmItem(orderItemId, orderPolicy, now));
    }

    /**
     * 자동 구매확정 대상을 좁힌다: 주문됨이고 (지금 − 자동 구매확정 기간) 이전에 배송 완료된 주문상품.
     * 주문상품 ID 오름차순 100건.
     */
    @Transactional(readOnly = true)
    public List<AutoConfirmTarget> findAutoConfirmTargets() {
        LocalDateTime deliveredBefore = LocalDateTime.now().minus(orderPolicy.autoConfirmAfter());
        return orderRepository.findAutoConfirmTargets(
                OrderItem.Status.ORDERED, deliveredBefore, PageRequest.of(0, AUTO_CONFIRM_BATCH_SIZE))
            .stream()
            .map(row -> new AutoConfirmTarget(row.getOrderId(), row.getOrderItemId()))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrder(Long memberId, Long orderId) {
        return orderRepository.findByIdAndMemberId(orderId, memberId)
            .map(order -> OrderDetail.from(order, deliveryRepository.findAllByOrderIdOrderByIdAsc(orderId)))
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 최근 주문부터.
     */
    @Transactional(readOnly = true)
    public Page<OrderSummary> getOrders(Long memberId, int page, int size) {
        return orderRepository.findAllByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size))
            .map(OrderSummary::from);
    }

    /**
     * 주문이 없으면 무시. 이번에 취소했을 때만 order.cancelled를 발행한다(사유는 주문에 기록된 값,
     * 주문상품 순서 그대로).
     *
     * @param source 취소를 일으킨 이벤트 · 작업 이름(로그용)
     * @param change 주문을 취소하는 도메인 메서드. 이번에 취소했으면 APPLIED
     */
    private void cancel(Long orderId, String source, Function<Order, TransitionResult> change) {
        LocalDateTime now = LocalDateTime.now();
        Order order = findForEvent(orderId, source);
        if (order == null) {
            return;
        }
        TransitionResult result = change.apply(order);
        if (result != TransitionResult.APPLIED) {
            logIgnored(source, order, result);
            return;
        }
        eventPublisher.publish(new OrderCancelledEvent(UUID.randomUUID(), now,
            new OrderCancelledEvent.Data(order.getId(), order.getMemberId(), order.getCancelReason().name(),
                order.getItems().stream()
                    .map(item -> new OrderCancelledEvent.Item(
                        item.getId(), item.getProduct().optionId(), item.getQuantity()))
                    .toList())));
        log.info("주문 취소: orderId={} reason={}", order.getId(), order.getCancelReason());
    }

    /**
     * 받은 이벤트(또는 시간 작업)의 주문을 찾는다. 없으면 WARN을 남기고 null.
     */
    private Order findForEvent(Long orderId, String event) {
        return orderRepository.findById(orderId).orElseGet(() -> {
            log.warn("이벤트의 주문 없음: event={} orderId={}", event, orderId);
            return null;
        });
    }

    private void logIgnored(String event, Order order, TransitionResult result) {
        log.debug("이벤트 무시: event={} orderId={} result={} status={}",
            event, order.getId(), result, order.getStatus());
    }

    /**
     * 이번에 확정했을 때만 order-item.purchase-confirmed를 발행한다.
     *
     * @param source 확정을 일으킨 요청 이름(로그용)
     * @param change 주문상품을 확정하는 도메인 메서드. 이번에 확정했으면 APPLIED
     */
    private void confirm(Order order, Long orderItemId, String source, Function<Order, TransitionResult> change) {
        TransitionResult result = change.apply(order);
        if (result != TransitionResult.APPLIED) {
            log.debug("이벤트 무시: event={} orderId={} orderItemId={} result={} status={}",
                source, order.getId(), orderItemId, result, order.getStatus());
            return;
        }
        OrderItem item = findItem(order, orderItemId);
        OrderItem.ProductSnapshot product = item.getProduct();
        eventPublisher.publish(new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), item.getConfirmedAt(),
            new OrderItemPurchaseConfirmedEvent.Data(
                item.getId(),
                order.getId(),
                order.getMemberId(),
                product.sellerId(),
                item.getChild() == null ? null : item.getChild().childId(),
                product.productId(),
                product.optionId(),
                product.size(),
                item.getQuantity(),
                item.getAmount(),
                item.getConfirmedAt())));
        log.info("구매확정: orderId={} orderItemId={} confirmType={}",
            order.getId(), item.getId(), item.getConfirmType());
    }

    private OrderItem findItem(Order order, Long orderItemId) {
        return order.getItems().stream()
            .filter(item -> Objects.equals(item.getId(), orderItemId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND));
    }

    private Order.RequestedItem toRequestedItem(PlaceOrderCommand.Item item) {
        ReplicaProduct product = productReplicaRepository.findByOptionId(item.optionId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.PRODUCT_OPTION_NOT_FOUND));
        Order.RequestedChild child = item.childId() == null ? null : toRequestedChild(item.childId());
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(product.getProductId(), product.getOptionId(), product.getSellerId(),
                product.getProductName(), product.getOptionName(), product.getSize(), product.unitPrice(),
                product.getCategoryId()),
            product.isPurchasable(),
            child,
            item.quantity(),
            item.unitPrice());
    }

    private Order.RequestedChild toRequestedChild(Long childId) {
        ReplicaChild child = childReplicaRepository.findByChildId(childId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.CHILD_NOT_FOUND));
        return new Order.RequestedChild(
            new OrderItem.ChildSnapshot(child.getChildId(), child.getName(), child.getHeightCm(), child.getWeightKg(),
                child.getBodyType()),
            child.getMemberId(),
            child.isDeleted());
    }
}
