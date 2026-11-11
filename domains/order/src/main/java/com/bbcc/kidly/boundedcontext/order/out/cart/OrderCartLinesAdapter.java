package com.bbcc.kidly.boundedcontext.order.out.cart;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCheckout;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderCartLines;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주문하기의 장바구니 칸 확인을 장바구니 컨텍스트의 들어오는 포트로 묻는다(#998). 주문하는 주체는 회원이다.
 */
@Component
@RequiredArgsConstructor
public class OrderCartLinesAdapter implements OrderCartLines {

    private static final String MEMBER = "MEMBER";

    private final CartCheckout cartCheckout;

    @Override
    public void requireOrderable(Long memberId, List<Line> lines) {
        cartCheckout.requireOrderable(new CartReservations.Payer(MEMBER, memberId),
            lines.stream().map(line -> new CartCheckout.Line(line.cartItemId(), line.optionId(), line.childId(),
                    line.quantity()))
                .toList());
    }
}
