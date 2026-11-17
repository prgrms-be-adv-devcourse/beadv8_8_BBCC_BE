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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
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

    private static final DateTimeFormatter CANCELLED_FROM_FORMAT =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

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
     * 주문 상태로 거른 내 주문 목록(#1280). status가 없으면(null) {@link #getOrders(Long, int, int)}와 같다.
     * 응답 status와 같은 이름 · 값 하나만 대문자 그대로 받고, 모르는 값 · 소문자 · 빈 값은 INVALID_ORDER_STATUS(400).
     * 거르기와 건수(totalElements)는 DB에서 같은 조건으로 하고, 요약 조립 규칙은 상태 없는 목록과 같다.
     */
    public Page<OrderSummary> getOrders(Long memberId, String status, int page, int size) {
        if (status == null) {
            return getOrders(memberId, page, size);
        }
        Order.Status filter = toOrderStatus(status);
        PageRequest pageable = PageRequest.of(page, size);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.countByMemberIdAndStatus(memberId, filter));
        }
        return toSummaries(orderRepository.findAllByMemberIdAndStatusOrderByIdDesc(memberId, filter, pageable));
    }

    /**
     * 주문 상태로 거른 관리자 전체 주문 목록(#1280). status가 없으면(null) {@link #getAllOrders(int, int)}와 같고,
     * 값 규칙 · 건수 · 조립은 {@link #getOrders(Long, String, int, int)}와 같다.
     */
    public Page<AdminOrderSummary> getAllOrders(String status, int page, int size) {
        if (status == null) {
            return getAllOrders(page, size);
        }
        Order.Status filter = toOrderStatus(status);
        PageRequest pageable = PageRequest.of(page, size);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.countByStatus(filter));
        }
        return toAdminSummaries(orderRepository.findAllByStatusOrderByIdDesc(filter, pageable));
    }

    /**
     * 관리자 전체 주문 목록(#1280 상태 필터 + #1325 취소 시각). 줄마다 cancelledAt(취소 주문만, 상태 이력의 취소 시각)을 페이지 단위로
     * 한 번에 읽어 붙인다(주문마다 조회하지 않음).
     * cancelledFrom(한국 시간 yyyy-MM-ddTHH:mm:ss, 포함)이 있으면 그 시각 이후 취소된 주문만이다: status 없이 와도 취소된 주문만,
     * status와 함께면 AND라 CANCELLED가 아니면 0건. 형식이 틀리면 INVALID_CANCELLED_FROM(400). 없으면 #1280과 같다.
     */
    public Page<AdminOrderSummary> getAllOrders(String status, String cancelledFrom, int page, int size) {
        if (cancelledFrom == null) {
            return withCancelledAt(getAllOrders(status, page, size));
        }
        LocalDateTime from = toCancelledFrom(cancelledFrom);
        Order.Status filter = status == null ? null : toOrderStatus(status);
        PageRequest pageable = PageRequest.of(page, size);
        if (filter != null && filter != Order.Status.CANCELLED) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, orderRepository.countCancelledSince(from));
        }
        return withCancelledAt(toAdminSummaries(orderRepository.findCancelledSince(from, pageable)));
    }

    // 페이지의 취소 주문만 모아 취소 시각을 한 번에 읽는다(취소 주문이 없으면 조회하지 않는다)
    private Page<AdminOrderSummary> withCancelledAt(Page<AdminOrderSummary> summaries) {
        List<Long> cancelledIds = summaries.getContent().stream()
            .filter(summary -> Order.Status.CANCELLED.name().equals(summary.status()))
            .map(AdminOrderSummary::orderId)
            .toList();
        if (cancelledIds.isEmpty()) {
            return summaries;
        }
        Map<Long, LocalDateTime> cancelledAt = historyRepository.findOrderCancelledAt(cancelledIds).stream()
            .collect(Collectors.toMap(OrderStatusHistoryRepository.CancelledAt::getOrderId,
                OrderStatusHistoryRepository.CancelledAt::getCancelledAt));
        return summaries.map(summary -> summary.withCancelledAt(cancelledAt.get(summary.orderId())));
    }

    // 한국 시간 그대로(오프셋 · 밀리초 없이 초까지). 날짜만 · 빈 값 · 없는 날짜는 받지 않는다
    private static LocalDateTime toCancelledFrom(String cancelledFrom) {
        try {
            return LocalDateTime.parse(cancelledFrom, CANCELLED_FROM_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(OrderErrorCode.INVALID_CANCELLED_FROM);
        }
    }

    // 상태 이름 그대로(대문자)만 받는다. 소문자 · 공백 · 빈 값을 너그럽게 맞추지 않는다(응답 status 값과 같게)
    private static Order.Status toOrderStatus(String status) {
        return Arrays.stream(Order.Status.values())
            .filter(value -> value.name().equals(status))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS));
    }

    // 상태로 거른 목록의 요약 조립(상태 없는 getOrders와 같은 규칙: 주문상품 · 상호명은 한 번씩 읽고, 못 읽은 주문은 대표 상품 없이)
    private Page<OrderSummary> toSummaries(Page<Order> orders) {
        if (orders.isEmpty()) {
            return orders.map(OrderSummary::withoutItems);
        }
        Map<Long, Order> withItems = withItems(orders);
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

    // 상태로 거른 관리자 목록의 요약 조립(상태 없는 getAllOrders와 같은 규칙)
    private Page<AdminOrderSummary> toAdminSummaries(Page<Order> orders) {
        if (orders.isEmpty()) {
            return orders.map(AdminOrderSummary::withoutItems);
        }
        Map<Long, Order> withItems = withItems(orders);
        return orders.map(order -> {
            Order loaded = withItems.get(order.getId());
            if (loaded == null) {
                log.warn("관리자 주문 목록 주문상품 없음: reason=order-items-not-found orderId={}", order.getId());
                return AdminOrderSummary.withoutItems(order);
            }
            return AdminOrderSummary.from(loaded);
        });
    }

    private Map<Long, Order> withItems(Page<Order> orders) {
        return orderRepository.findAllWithItemsByIdIn(orders.getContent().stream().map(Order::getId).toList())
            .stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
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
