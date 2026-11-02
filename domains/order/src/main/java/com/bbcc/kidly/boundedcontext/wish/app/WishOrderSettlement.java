package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishItemRepository;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishOrders;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishOrders.OrderState;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.OrderResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 바로 결제한 조르기에 주문의 결제 결과를 반영한다(#701). 주문 컨텍스트는 조르기를 모르므로(컨텍스트 규칙) 조르기가 주문을
 * 읽어 맞춘다: 그 자녀의 조르기를 다루거나 볼 때마다 결과를 기다리는 조르기의 주문 상태를 {@link WishOrders}로 읽어
 * 결제된 적이 있으면 승인한 줄을 공용 장바구니에서 빼고(PURCHASED), 결제 없이 취소됐으면 잠금만 푼다. 결제대기면 그대로 둔다.
 * <p>
 * 쓰기 트랜잭션에서는 그대로 저장된다. 읽기 전용 트랜잭션(보기)에서도 같은 판단을 하지만 저장하지 않는다(Hibernate 읽기 전용
 * 세션은 바뀐 엔티티를 flush하지 않는다): 보기 결과는 반영한 것과 같고, 저장은 다음 쓰기 때 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
class WishOrderSettlement {

    private final WishRequestRepository wishRequestRepository;
    private final WishItemRepository wishItemRepository;
    private final WishOrders wishOrders;

    void settle(Long childId) {
        List<WishRequest> waiting = wishRequestRepository.findAllByChildIdAndStatusAndCheckoutAndOrderResultIsNull(
                childId, WishRequest.Status.APPROVED, WishRequest.Checkout.ORDER).stream()
            .filter(WishRequest::isAwaitingOrder)
            .toList();
        if (waiting.isEmpty()) {
            return;
        }
        Map<Long, OrderState> states = wishOrders.findStates(waiting.stream().map(WishRequest::getOrderId).toList())
            .stream()
            .collect(Collectors.toMap(OrderState::orderId, Function.identity()));
        Map<Long, WishItem> items = wishItemRepository.findAllByIdIn(
                waiting.stream().flatMap(request -> request.wishItemIds().stream()).toList())
            .stream()
            .collect(Collectors.toMap(WishItem::getId, Function.identity()));
        boolean saved = !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        for (WishRequest request : waiting) {
            OrderState state = states.get(request.getOrderId());
            if (state == null) {
                continue;
            }
            List<WishItem> requestItems = request.wishItemIds().stream()
                .map(items::get)
                .filter(Objects::nonNull)
                .toList();
            Optional<OrderResult> result = request.settleOrder(state.paid(), state.cancelled(), requestItems);
            if (saved && result.isPresent()) {
                log.info("조르기 결제 결과 반영: requestId={} orderId={} result={}",
                    request.getId(), request.getOrderId(), result.get());
            }
        }
    }
}
