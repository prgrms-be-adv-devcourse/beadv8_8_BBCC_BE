package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrors;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.TransitionResult;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매확정(#1508, OrderService에서 분리): 구매자 확정 · 자동 구매확정과 그 대상 찾기. 확정하면 order-item.purchase-confirmed를
 * 같은 트랜잭션에서 낸다. 로그: 확정하면 INFO, 받은 요청을 무시하면 DEBUG(결과 · 현재 상태), 주문이 없으면 WARN.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final OrderRepository orderRepository;
    private final SellerReplicaRepository sellerReplicaRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final Order.Policy orderPolicy;
    private final OrderEventSupport events;

    /**
     * 구매자가 직접 구매확정한다. 없거나 남의 주문이면 404. 이미 확정이어도 주문상품을 돌려준다.
     */
    public OrderDetail.Item confirmByBuyer(Long memberId, Long orderId, Long orderItemId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        try {
            confirm(order, orderItemId, "confirm-by-buyer", OrderActor.buyer(memberId, OrderActor.BUYER_CONFIRM),
                target -> target.confirmItem(orderItemId, now));
        } catch (OrderRuleViolationException e) {
            throw OrderErrors.toBusiness(e);
        }
        OrderItem item = findItem(order, orderItemId);
        String sellerName = sellerReplicaRepository.findBySellerId(item.getProduct().sellerId())
            .map(ReplicaSeller::getBusinessName)
            .orElse(null);
        return OrderDetail.Item.from(item, sellerName);
    }

    /**
     * 자동 구매확정. 기간이 지났는지는 주문이 직접 확인한다(기간 전 · 이미 확정이면 그대로). 주문이 없으면 무시.
     */
    public void confirmAutomatically(Long orderId, Long orderItemId) {
        LocalDateTime now = LocalDateTime.now();
        Order order = events.findForEvent(orderId, "auto-confirm");
        if (order == null) {
            return;
        }
        confirm(order, orderItemId, "auto-confirm", OrderActor.system(OrderActor.AUTO_CONFIRM),
            target -> target.autoConfirmItem(orderItemId, orderPolicy, now));
    }

    /**
     * 자동 구매확정 대상을 좁힌다: 주문됨이고 (now − 자동 구매확정 기간) 이전(포함)에 배송 완료된 주문상품.
     * afterId 다음부터 주문상품 ID 오름차순 limit건. now는 스케줄러가 한 실행에 한 번 정해 넘긴다.
     */
    @Transactional(readOnly = true)
    public List<AutoConfirmTarget> findAutoConfirmTargets(LocalDateTime now, Long afterId, int limit) {
        LocalDateTime deliveredBefore = now.minus(orderPolicy.autoConfirmAfter());
        return orderRepository.findAutoConfirmTargets(
                OrderItem.Status.ORDERED, deliveredBefore, afterId, Limit.of(limit))
            .stream()
            .map(row -> new AutoConfirmTarget(row.getOrderId(), row.getOrderItemId()))
            .toList();
    }

    /**
     * 이번에 확정했을 때만 order-item.purchase-confirmed를 발행한다.
     *
     * @param source 확정을 일으킨 요청 이름(로그용)
     * @param actor 상태 이력에 남길 주체
     * @param change 주문상품을 확정하는 도메인 메서드. 이번에 확정했으면 APPLIED
     */
    private void confirm(Order order, Long orderItemId, String source, OrderActor actor,
        Function<Order, TransitionResult> change) {
        OrderItem.Status from = order.getItems().stream()
            .filter(item -> Objects.equals(item.getId(), orderItemId))
            .map(OrderItem::getStatus)
            .findFirst()
            .orElse(null);
        TransitionResult result = change.apply(order);
        if (result != TransitionResult.APPLIED) {
            log.debug("이벤트 무시: event={} orderId={} orderItemId={} result={} status={}",
                source, order.getId(), orderItemId, result, order.getStatus());
            return;
        }
        OrderItem item = findItem(order, orderItemId);
        historyRepository.save(actor.history(order.getId(), OrderStatusHistory.TargetType.ORDER_ITEM, item.getId(),
            from, item.getStatus(), item.getConfirmType().name(), item.getConfirmedAt()));
        OrderItem.ProductSnapshot product = item.getProduct();
        events.publish(new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), item.getConfirmedAt(),
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
                item.getConfirmedAt(),
                item.getConfirmType().name())));
        log.info("구매확정: orderId={} orderItemId={} confirmType={}",
            order.getId(), item.getId(), item.getConfirmType());
    }

    private OrderItem findItem(Order order, Long orderItemId) {
        return order.getItems().stream()
            .filter(item -> Objects.equals(item.getId(), orderItemId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND));
    }
}
