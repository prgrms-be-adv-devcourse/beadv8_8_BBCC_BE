package com.bbcc.kidly.boundedcontext.order.out.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderedCartCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제완료된 주문이 산 칸을 장바구니 컨텍스트의 들어오는 포트로 뺀다(#641). 장바구니는 주문을 읽지 않으므로(#998) 이 어댑터가 주문을 읽어
 * 지울 칸 정보(주문상품 ID · 장바구니 칸 ID · 구매자 · 옵션 · 자녀)를 넘긴다. 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class OrderedCartCleanerAdapter implements OrderedCartCleaner {

    private final CartCleanup cartCleanup;
    private final OrderRepository orderRepository;

    @Override
    public void clearOrdered(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> cartCleanup.clearOrdered(orderId,
            order.getItems().stream()
                .map(item -> new CartCleanup.OrderedLine(item.getId(), item.getCartItemId(), order.getMemberId(),
                    item.getProduct().optionId(), item.getChild() == null ? null : item.getChild().childId()))
                .toList()));
    }
}
