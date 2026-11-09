package com.bbcc.kidly.boundedcontext.order.app.provided;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository.SellerOrderItemRow;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder;
import com.bbcc.kidly.boundedcontext.order.app.port.in.DeliveryRecorder.DeliveredItem;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup.OrderView;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup.SellerOrderView;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 조회 · 배송 반영을 다른 컨텍스트에 내준다(#617 · #641, {@link OrderLookup} · {@link DeliveryRecorder}). 부르는 쪽 트랜잭션에 합류한다(REQUIRED).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProvidedOrderService implements OrderLookup, DeliveryRecorder {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final Order.Policy orderPolicy;

    @Override
    public Optional<OrderView> findOrder(Long orderId) {
        return orderRepository.findById(orderId).map(ProvidedOrderService::toView);
    }

    /**
     * 주문상품 줄(주문 값 포함)을 쿼리 한 번으로 읽어 주문별로 묶는다(주문 ID 순서 그대로). 자녀 칸은 읽지 않는다(#997).
     */
    @Override
    public List<SellerOrderView> findOrdersForSeller(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SellerOrderItemRow>> byOrder = new LinkedHashMap<>();
        orderRepository.findSellerOrderItemsByOrderIdIn(orderIds)
            .forEach(row -> byOrder.computeIfAbsent(row.getOrderId(), id -> new ArrayList<>()).add(row));
        return byOrder.values().stream().map(ProvidedOrderService::toSellerView).toList();
    }

    @Override
    public List<DeliveredItem> recordDelivered(Long orderId, Long sellerId, LocalDateTime deliveredAt) {
        return orderRepository.findById(orderId)
            .map(order -> order.recordDelivered(sellerId, deliveredAt).stream()
                .map(item -> new DeliveredItem(item.getId(), item.getProduct().productId(), item.getQuantity(),
                    item.getDeliveredAt(), item.autoConfirmAt(orderPolicy.autoConfirmAfter())))
                .toList())
            .orElse(List.of());
    }

    @Override
    public void recordDeliveryHistory(Long orderId, Long deliveryId, HistoryActor actor, String from, String to,
        String reason, LocalDateTime occurredAt) {
        historyRepository.save(OrderStatusHistory.of(orderId, OrderStatusHistory.TargetType.DELIVERY, deliveryId, from,
            to, reason, OrderStatusHistory.ActorType.valueOf(actor.type()), actor.id(), actor.source(), occurredAt));
    }

    private static SellerOrderView toSellerView(List<SellerOrderItemRow> rows) {
        SellerOrderItemRow first = rows.getFirst();
        OrderView.ShippingAddress address = first.getRecipientName() == null && first.getPhone() == null
            && first.getZipCode() == null && first.getAddress() == null ? null
            : new OrderView.ShippingAddress(first.getRecipientName(), first.getPhone(), first.getZipCode(),
                first.getAddress());
        return new SellerOrderView(first.getOrderId(), first.getMemberId(), first.getOrderedAt(), address,
            rows.stream().map(row -> new SellerOrderView.SellerItem(row.getOrderItemId(), row.getProductId(),
                row.getOptionId(), row.getSellerId(), row.getProductName(), row.getOptionName(), row.getSize(),
                row.getUnitPrice(), row.getQuantity(), row.getAmount(), row.getStatus().name(), row.getDeliveredAt(),
                row.getConfirmedAt(), row.getConfirmType() == null ? null : row.getConfirmType().name(),
                row.getUnavailableReason())).toList());
    }

    private static OrderView toView(Order order) {
        Order.ShippingAddress address = order.getShippingAddress();
        return new OrderView(order.getId(), order.getMemberId(), order.getCreatedAt(),
            address == null ? null : new OrderView.ShippingAddress(address.recipientName(), address.phone(),
                address.zipCode(), address.address()),
            order.getItems().stream().map(ProvidedOrderService::toItem).toList());
    }

    private static OrderView.Item toItem(OrderItem item) {
        OrderItem.ProductSnapshot product = item.getProduct();
        OrderItem.ChildSnapshot child = item.getChild();
        return new OrderView.Item(
            item.getId(),
            product.productId(),
            product.optionId(),
            product.sellerId(),
            product.productName(),
            product.optionName(),
            product.size(),
            product.unitPrice(),
            item.getQuantity(),
            item.getAmount(),
            item.getStatus().name(),
            item.getDeliveredAt(),
            item.getConfirmedAt(),
            item.getConfirmType() == null ? null : item.getConfirmType().name(),
            child == null ? null : new OrderView.Child(child.childId(), child.name(), child.heightCm(),
                child.weightKg(), child.bodyType()),
            item.getUnavailableReason());
    }
}
