package com.bbcc.kidly.boundedcontext.order.app.order;

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
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문하기 · 결제 요청 · 주문 조회. 남의 주문은 없는 주문으로 본다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

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
        return OrderDetail.from(order, List.of());
    }

    /**
     * 재고 차감이 끝난 주문의 결제를 요청한다. 주문상품별 금액을 함께 보낸다(결제가 주문상품 단위로 결제 · 환불).
     * 주문이 없거나 이미 요청했거나 결제대기가 아니면 아무것도 하지 않는다.
     */
    public void requestPayment(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        orderRepository.findById(orderId)
            .filter(order -> order.requestPayment(now))
            .ifPresent(order -> eventPublisher.publish(new OrderPaymentRequestedEvent(UUID.randomUUID(), now,
                new OrderPaymentRequestedEvent.Data(order.getId(), order.getMemberId(), order.getTotalAmount(),
                    order.getItems().stream()
                        .map(item -> new OrderPaymentRequestedEvent.Item(item.getId(), item.getAmount()))
                        .toList()))));
    }

    /**
     * 결제완료로 바꾼다. 결제 시각은 이벤트 값이 아니라 지금 시각을 쓴다.
     *
     * @return 이번에 결제완료로 바꿨으면 true. 주문이 없거나 결제대기가 아니면 false
     */
    public boolean markPaid(Long orderId, Long paymentId) {
        LocalDateTime now = LocalDateTime.now();
        return orderRepository.findById(orderId)
            .map(order -> order.markPaid(paymentId, now))
            .orElse(false);
    }

    /**
     * 그 판매자 몫 주문상품에 배송 완료 시각을 기록한다. 이미 기록돼 있으면 그대로 둔다.
     */
    public void recordDelivered(Long orderId, Long sellerId, LocalDateTime at) {
        orderRepository.findById(orderId).ifPresent(order -> order.recordDelivered(sellerId, at));
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

    private Order.RequestedItem toRequestedItem(PlaceOrderCommand.Item item) {
        ReplicaProduct product = productReplicaRepository.findByOptionId(item.optionId())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.PRODUCT_OPTION_NOT_FOUND));
        Order.RequestedChild child = item.childId() == null ? null : toRequestedChild(item.childId());
        return new Order.RequestedItem(
            new OrderItem.ProductSnapshot(product.getProductId(), product.getOptionId(), product.getSellerId(),
                product.getProductName(), product.getSize(), product.unitPrice(), product.getCategoryId()),
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
