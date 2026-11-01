package com.bbcc.kidly.boundedcontext.order.out.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderedCartCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제완료된 주문의 상품을 장바구니 컨텍스트의 들어오는 포트로 뺀다(#641). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
public class OrderedCartCleanerAdapter implements OrderedCartCleaner {

    private final CartCleanup cartCleanup;

    @Override
    public void clearOrdered(Long orderId) {
        cartCleanup.clearOrdered(orderId);
    }
}
