package com.bbcc.kidly.boundedcontext.order.in.web.internal;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.dto.SellerOrderItemSummary;
import com.bbcc.kidly.shared.order.out.SellerOrderItems;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 사이 전용 판매자 주문상품 조회(#1120, 판매자 탈퇴 #1091의 남은 주문 확인). 주문 계약 {@link SellerOrderItems}(shared)를
 * 같은 모양({@link SellerOrderItemSummary})으로 HTTP에 낸다. 주문은 사실(미종결 주문상품 수)만 주고, 그 값으로 무엇을 할지(탈퇴 허용
 * 등)는 부르는 쪽이 정한다. 사용자 인증 헤더를 요구하지 않는다: 밖에서는 Gateway가 /internal/**을 막는다.
 */
@RestController
@RequiredArgsConstructor
public class SellerOrderItemController {

    public static final String SELLER_ORDER_ITEMS_SUMMARY_PATH = "/internal/sellers/{sellerId}/order-items/summary";

    private final SellerOrderItems sellerOrderItems;

    /**
     * 200 + 판매자 ID · 미종결 주문상품 수(주문이 없으면 0). 판매자 ID가 숫자가 아니거나 1 미만이면 400.
     */
    @GetMapping(SELLER_ORDER_ITEMS_SUMMARY_PATH)
    public SellerOrderItemSummary summary(@PathVariable String sellerId) {
        return sellerOrderItems.summarize(parseSellerId(sellerId));
    }

    // 숫자가 아닌 값 · 범위를 넘는 값 · 1 미만은 받는 쪽이 고칠 수 있는 같은 400으로 답한다
    private static Long parseSellerId(String value) {
        long id;
        try {
            id = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(OrderErrorCode.INVALID_SELLER_ID);
        }
        if (id < 1) {
            throw new BusinessException(OrderErrorCode.INVALID_SELLER_ID);
        }
        return id;
    }
}
