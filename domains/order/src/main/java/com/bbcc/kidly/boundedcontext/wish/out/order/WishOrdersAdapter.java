package com.bbcc.kidly.boundedcontext.wish.out.order;

import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderPlacing;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishOrders;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 바로 결제를 주문 컨텍스트의 주문하기 포트로 넘기고 결제 결과를 읽는다(#701 · #641). 같은 앱 안의 동기 호출이라
 * 부르는 쪽 트랜잭션에 합류한다. 주문자는 보호자, 주문상품의 자녀는 조른 자녀다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishOrdersAdapter implements WishOrders {

    private static final String CANCELLED = "CANCELLED";

    private final OrderPlacing orderPlacing;

    @Override
    public Long place(Long memberId, Long childId, List<Line> lines, ShippingAddress shippingAddress) {
        return orderPlacing.place(memberId,
            lines.stream()
                .map(line -> new OrderPlacing.Item(line.optionId(), childId, line.quantity(), line.unitPrice()))
                .toList(),
            new OrderPlacing.ShippingAddress(shippingAddress.recipientName(), shippingAddress.phone(),
                shippingAddress.zipCode(), shippingAddress.address()));
    }

    @Override
    public List<OrderState> findStates(Collection<Long> orderIds) {
        return orderPlacing.findPaymentStates(orderIds).stream()
            .map(state -> new OrderState(state.orderId(), state.paid(), CANCELLED.equals(state.status())))
            .toList();
    }
}
