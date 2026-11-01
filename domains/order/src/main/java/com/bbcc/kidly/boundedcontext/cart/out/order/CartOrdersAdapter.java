package com.bbcc.kidly.boundedcontext.cart.out.order;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartOrders;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문한 상품을 주문 컨텍스트의 주문 조회 포트로 읽는다(#641). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class CartOrdersAdapter implements CartOrders {

    private final OrderLookup orderLookup;

    @Override
    public List<OrderedLine> findOrderedLines(Long orderId) {
        return orderLookup.findOrder(orderId)
            .map(order -> order.items().stream()
                .map(item -> new OrderedLine(order.memberId(), item.optionId(),
                    item.child() == null ? null : item.child().childId()))
                .toList())
            .orElse(List.of());
    }
}
