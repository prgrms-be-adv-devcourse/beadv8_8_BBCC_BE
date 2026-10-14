package com.bbcc.kidly.boundedcontext.order.app.delivery;

import com.bbcc.kidly.boundedcontext.order.app.delivery.dto.DeliveryResult;
import com.bbcc.kidly.boundedcontext.order.app.delivery.dto.SellerOrderView;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자별 주문(배송): 결제완료 뒤 만들기 · 발송 · 배송 완료 · 판매자 목록. 남의 배송은 없는 배송으로 본다.
 * 주문은 읽기만 한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문상품을 판매자로 묶어 판매자마다 배송 준비 1건을 만든다. 주문이 없으면 만들지 않는다.
     */
    public void createFor(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> order.getItems().stream()
            .map(item -> item.getProduct().sellerId())
            .distinct()
            .forEach(sellerId -> deliveryRepository.save(Delivery.create(orderId, sellerId))));
    }

    /**
     * 확인 순서: 모르는 택배사 400 → 없거나 남의 배송 404 → 발송 전이 아니면 409.
     */
    public DeliveryResult ship(Long memberId, Long deliveryId, String carrier, String trackingNumber) {
        Delivery.Carrier parsed = toCarrier(carrier);
        Delivery delivery = findOwned(memberId, deliveryId);
        delivery.ship(parsed, trackingNumber, LocalDateTime.now());
        return DeliveryResult.from(delivery);
    }

    /**
     * 배송 완료. 이미 완료면 그대로 처음 값을 돌려준다. 발송 전이면 409.
     */
    public DeliveryResult complete(Long memberId, Long deliveryId) {
        Delivery delivery = findOwned(memberId, deliveryId);
        delivery.complete(LocalDateTime.now());
        return DeliveryResult.from(delivery);
    }

    /**
     * 판매자의 배송을 최근 것부터. 배송 페이지 1번 + 그 페이지 주문(주문상품 포함) 1번으로 읽는다(건마다 조회하지 않음).
     */
    @Transactional(readOnly = true)
    public Page<SellerOrderView> getSellerOrders(Long memberId, int page, int size) {
        Page<Delivery> deliveries =
            deliveryRepository.findAllBySellerIdOrderByIdDesc(memberId, PageRequest.of(page, size));
        Map<Long, Order> ordersById = findOrdersWithItems(deliveries.getContent());
        return deliveries.map(delivery -> SellerOrderView.of(delivery, orderOf(ordersById, delivery)));
    }

    private Map<Long, Order> findOrdersWithItems(List<Delivery> deliveries) {
        if (deliveries.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = deliveries.stream().map(Delivery::getOrderId).distinct().toList();
        return orderRepository.findAllWithItemsByIdIn(orderIds).stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
    }

    private Order orderOf(Map<Long, Order> ordersById, Delivery delivery) {
        Order order = ordersById.get(delivery.getOrderId());
        if (order == null) {
            throw new IllegalStateException("배송의 주문이 없습니다: " + delivery.getOrderId());
        }
        return order;
    }

    private Delivery findOwned(Long memberId, Long deliveryId) {
        return deliveryRepository.findByIdAndSellerId(deliveryId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.DELIVERY_NOT_FOUND));
    }

    private Delivery.Carrier toCarrier(String carrier) {
        return Arrays.stream(Delivery.Carrier.values())
            .filter(candidate -> candidate.name().equals(carrier))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_CARRIER));
    }
}
