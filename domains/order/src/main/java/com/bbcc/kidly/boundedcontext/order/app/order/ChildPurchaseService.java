package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase.CANCELLED;
import static com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase.CONFIRMED;
import static com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase.DELIVERED;
import static com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase.PAID;
import static com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase.SHIPPING;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.ChildPurchase;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeliveries;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository.ChildPurchaseRow;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀가 보는 나를 위해 산 상품 내역(#1000, BEG-07). 자기 프로필이 입을 아이로 지정된 주문상품 중 결제된 적 있는 주문의 것만
 * (결제대기 · 결제 없이 취소된 주문은 빠진다), 주문 일시 최신순(같으면 주문상품 ID 큰 것부터). 주문 안에서만 쓰므로 들어오는 포트가
 * 아니라 app 서비스다(조르기는 주문을 읽지 않는다: 출처는 화면이 조르기 응답의 결제된 주문상품 ID와 맞춘다).
 * 상태는 주문상품 · 주문 · 배송으로 정한다:
 * 취소(주문상품 취소 또는 주문 취소) → 구매확정 → 배송 완료(주문상품 deliveredAt) → 배송 중(그 판매자 배송이 발송됨) → 결제 완료.
 * 배송 중은 그 쪽 배송을 한 페이지에 한 번 읽어(주문 ID 묶음) 판단한다(줄마다 조회하지 않음).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChildPurchaseService {

    private final OrderRepository orderRepository;
    private final OrderDeliveries orderDeliveries;

    /**
     * @param childId 로그인한 자녀의 프로필 ID(호출 주체 확인은 부르는 쪽이 한다)
     */
    public Page<ChildPurchase> getPurchases(Long childId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다(구매자 주문 목록과 같음)
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Page<ChildPurchaseRow> rows = orderRepository.findChildPurchases(childId, pageable);
        Set<String> shipped = shippedSellers(rows.getContent());
        return rows.map(row -> new ChildPurchase(row.getOrderItemId(), row.getOrderedAt(), row.getProductId(),
            row.getProductName(), row.getOptionName(), row.getQuantity(), statusOf(row, shipped),
            row.getThumbnailUrl()));
    }

    // 발송된 배송의 (주문 ID, 판매자 ID). 아직 배송 완료 전인 주문상품만 필요하다
    private Set<String> shippedSellers(List<ChildPurchaseRow> rows) {
        List<Long> orderIds = rows.stream()
            .filter(row -> row.getDeliveredAt() == null && row.getItemStatus() == OrderItem.Status.ORDERED
                && row.getOrderStatus() == Order.Status.PAID)
            .map(ChildPurchaseRow::getOrderId)
            .distinct()
            .toList();
        if (orderIds.isEmpty()) {
            return Set.of();
        }
        return orderDeliveries.findByOrders(orderIds).stream()
            .filter(OrderDeliveries.Delivery::shipped)
            .map(delivery -> key(delivery.orderId(), delivery.sellerId()))
            .collect(Collectors.toSet());
    }

    private static String statusOf(ChildPurchaseRow row, Set<String> shipped) {
        if (row.getItemStatus() == OrderItem.Status.CANCELLED || row.getOrderStatus() == Order.Status.CANCELLED) {
            return CANCELLED;
        }
        if (row.getItemStatus() == OrderItem.Status.CONFIRMED) {
            return CONFIRMED;
        }
        if (row.getDeliveredAt() != null) {
            return DELIVERED;
        }
        if (shipped.contains(key(row.getOrderId(), row.getSellerId()))) {
            return SHIPPING;
        }
        return PAID;
    }

    private static String key(Long orderId, Long sellerId) {
        return orderId + ":" + sellerId;
    }
}
