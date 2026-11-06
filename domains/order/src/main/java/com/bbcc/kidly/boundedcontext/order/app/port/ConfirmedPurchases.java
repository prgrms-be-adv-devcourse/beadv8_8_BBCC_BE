package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * 주문이 보유한 구매확정 사실. 다른 도메인이나 전달 이력의 DB는 읽지 않는다.
 */
public interface ConfirmedPurchases {

    long upperBound();

    List<Purchase> findAfter(long afterId, long throughId, Limit limit);

    interface Purchase {
        Long getOrderItemId();
        Long getOrderId();
        Long getMemberId();
        Long getSellerId();
        Long getChildId();
        Long getProductId();
        Long getOptionId();
        String getSize();
        int getQuantity();
        long getAmount();
        LocalDateTime getConfirmedAt();

        /**
         * 저장된 구매확정 방식(#901). 없으면 null.
         */
        OrderItem.ConfirmType getConfirmType();
    }
}
