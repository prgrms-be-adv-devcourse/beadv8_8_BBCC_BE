package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AdminOrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderStatusHistoryResult;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import com.bbcc.kidly.shared.order.dto.SellerOrderItemSummary;
import com.bbcc.kidly.shared.order.out.MemberOrderHistory;
import com.bbcc.kidly.shared.order.out.SellerOrderItems;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 주문 상세 · 목록 · 타임라인과 관리자 목록 · 이력 조회. 남의 주문은 없는 주문으로 본다.
 * 응답 조립은 변경 서비스도 같은 트랜잭션 안에서 재사용한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService implements SellerOrderItems, MemberOrderHistory {

    private final OrderRepository orderRepository;
    private final OrderDeliveries orderDeliveries;
    private final SellerReplicaRepository sellerReplicaRepository;
    private final OrderStatusHistoryRepository historyRepository;

    /**
     * 그 판매자 상품의 미종결 주문상품 수(#1120, {@link SellerOrderItems}): 주문이 취소되지 않았고 주문상품이 주문됨(ORDERED)인 수
     * (결제대기 · 결제완료 · 배송 준비 · 배송 중 · 배송 완료 후 구매확정 전). 구매확정 · 취소된 주문상품과 취소된 주문의 주문상품은 빼고,
     * 주문이 없으면 0. 그 값으로 무엇을 할지는 부르는 쪽이 정한다.
     */
    @Override
    public SellerOrderItemSummary summarize(Long sellerId) {
        return new SellerOrderItemSummary(sellerId, orderRepository.countSellerItems(sellerId,
            OrderItem.Status.ORDERED, Order.Status.CANCELLED));
    }

    /**
     * 회원의 최근 주문 · 배송({@link MemberOrderHistory}, #1191 챗봇). 주문 · 주문상품 · 배송을 한 번씩 읽는다.
     */
    @Override
    public List<MemberOrderSummary> recent(Long memberId, int limit) {
        List<Order> orders = orderRepository.findAllByMemberIdOrderByIdDesc(memberId, PageRequest.of(0, limit))
            .getContent();
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Order> withItems = orderRepository.findAllWithItemsByIdIn(orderIds).stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, List<OrderDeliveries.Delivery>> deliveries = orderDeliveries.findByOrders(orderIds).stream()
            .collect(Collectors.groupingBy(OrderDeliveries.Delivery::orderId));
        return orders.stream()
            .map(order -> memberOrderOf(withItems.getOrDefault(order.getId(), order),
                deliveries.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    private static MemberOrderSummary memberOrderOf(Order order, List<OrderDeliveries.Delivery> deliveries) {
        // 판매자마다 배송이 하나다. 같은 판매자 배송이 여럿이면 나중에 만든 것을 쓴다
        Map<Long, OrderDeliveries.Delivery> bySeller = deliveries.stream()
            .collect(Collectors.toMap(OrderDeliveries.Delivery::sellerId, Function.identity(),
                (first, later) -> later));
        List<MemberOrderSummary.Item> items = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(item -> new MemberOrderSummary.Item(item.getProduct().productName(), item.getProduct().optionName(),
                item.getQuantity(), item.getStatus().name(),
                deliveryOf(bySeller.get(item.getProduct().sellerId()))))
            .toList();
        return new MemberOrderSummary(order.getId(), order.getCreatedAt(), order.getStatus().name(), items);
    }

    private static MemberOrderSummary.Delivery deliveryOf(OrderDeliveries.Delivery delivery) {
        if (delivery == null) {
            return null;
        }
        return new MemberOrderSummary.Delivery(delivery.status(), delivery.carrier(), delivery.shippedAt(),
            delivery.deliveredAt());
    }

    public OrderDetail getOrder(Long memberId, Long orderId) {
        return orderRepository.findByIdAndMemberId(orderId, memberId)
            .map(order -> detailOf(orderId, order))
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    OrderDetail detailOf(Long orderId, Order order) {
        return OrderDetail.from(order, orderDeliveries.findByOrder(orderId),
            sellerNamesOf(List.of(order)));
    }

    /**
     * 주문(주문상품 포함) · 배송 건 · 판매자 상호명을 한 번씩 읽어 타임라인을 만든다(#352). 남의 주문은 없는 주문으로 본다.
     */
    public OrderTimeline getTimeline(Long memberId, Long orderId) {
        Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        return OrderTimeline.from(order, orderDeliveries.findByOrder(orderId),
            sellerNamesOf(List.of(order)));
    }

    /**
     * 관리자용 주문 상태 이력(#442). 오래된 것부터(같은 시각은 저장 순서). 주문이 없으면 404, 이력이 없으면 빈 목록.
     */
    public List<OrderStatusHistoryResult> getHistory(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return historyRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
            .map(OrderStatusHistoryResult::from)
            .toList();
    }

    /**
     * 최근 주문부터. 그 페이지 주문들의 주문상품 · 판매자 상호명은 한 번씩 읽어 합친다(주문 수와 상관없이 쿼리 수 일정).
     * 주문상품을 읽지 못한 주문은 목록 전체를 실패시키지 않고 대표 상품명 없이 내리고 WARN만 남긴다.
     */
    public Page<OrderSummary> getOrders(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.countByMemberId(memberId));
        }
        Page<Order> orders = orderRepository.findAllByMemberIdOrderByIdDesc(memberId, pageable);
        if (orders.isEmpty()) {
            return orders.map(OrderSummary::withoutItems);
        }
        Map<Long, Order> withItems = orderRepository.findAllWithItemsByIdIn(
                orders.getContent().stream().map(Order::getId).toList())
            .stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(withItems.values());
        return orders.map(order -> {
            Order loaded = withItems.get(order.getId());
            if (loaded == null) {
                log.warn("주문 목록 주문상품 없음: reason=order-items-not-found orderId={}", order.getId());
                return OrderSummary.withoutItems(order);
            }
            return OrderSummary.from(loaded, sellerNames);
        });
    }

    /**
     * 관리자 전체 주문 목록. 최근 주문부터, 그 페이지 주문들의 주문상품은 한 번에 읽어 주문상품 수를 센다
     * (주문 수와 상관없이 쿼리 수 일정). 주문상품을 읽지 못한 주문은 주문상품 수 0으로 내리고 WARN만 남긴다.
     */
    public Page<AdminOrderSummary> getAllOrders(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.count());
        }
        Page<Order> orders = orderRepository.findAllByOrderByIdDesc(pageable);
        if (orders.isEmpty()) {
            return orders.map(AdminOrderSummary::withoutItems);
        }
        Map<Long, Order> withItems = orderRepository.findAllWithItemsByIdIn(
                orders.getContent().stream().map(Order::getId).toList())
            .stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        return orders.map(order -> {
            Order loaded = withItems.get(order.getId());
            if (loaded == null) {
                log.warn("관리자 주문 목록 주문상품 없음: reason=order-items-not-found orderId={}", order.getId());
                return AdminOrderSummary.withoutItems(order);
            }
            return AdminOrderSummary.from(loaded);
        });
    }

    /**
     * 주문들의 주문상품 판매자 상호명을 한 번에 읽는다(판매자마다 조회하지 않음). 복사본이 없는 판매자는 빠진다.
     */
    Map<Long, String> sellerNamesOf(Collection<Order> orders) {
        Set<Long> sellerIds = orders.stream()
            .flatMap(order -> order.getItems().stream())
            .map(item -> item.getProduct().sellerId())
            .collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return sellerReplicaRepository.findAllBySellerIdIn(sellerIds).stream()
            .collect(Collectors.toMap(ReplicaSeller::getSellerId, ReplicaSeller::getBusinessName));
    }

}
