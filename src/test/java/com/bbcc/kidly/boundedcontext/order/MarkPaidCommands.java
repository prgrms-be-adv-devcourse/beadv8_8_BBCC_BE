package com.bbcc.kidly.boundedcontext.order;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;

/**
 * 테스트에서 이벤트 대신 서비스를 바로 불러 결제완료로 만들 때 쓰는, 주문과 그대로 맞는 결제 완료 입력.
 */
public final class MarkPaidCommands {

    private MarkPaidCommands() {
    }

    /**
     * 주문한 회원 · 총액 · 주문상품별 금액이 모두 주문과 같은 결제 완료.
     */
    public static MarkPaidCommand matching(OrderDetail order, Long memberId, Long paymentId) {
        return new MarkPaidCommand(order.orderId(), paymentId, memberId, order.totalAmount(),
            order.items().stream()
                .map(item -> new MarkPaidCommand.Item(item.orderItemId(), item.amount()))
                .toList());
    }
}
