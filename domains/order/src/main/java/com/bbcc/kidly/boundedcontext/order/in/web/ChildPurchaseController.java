package com.bbcc.kidly.boundedcontext.order.in.web;

import com.bbcc.kidly.boundedcontext.order.app.order.ChildPurchaseService;
import com.bbcc.kidly.boundedcontext.order.in.web.dto.ChildPurchaseResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자녀가 보는 나를 위해 산 상품 내역 API(#1000, BEG-07). 자원은 주문상품이고 "나(로그인한 자녀)를 입을 아이로 지정한 것"이라
 * 주문 아래 {@code /api/v1/orders/items/mine}에 둔다(주문 라우트 /api/v1/orders/**를 그대로 타 Gateway 변경 없음). 자녀 로그인만
 * ({@link OrderChildCaller}: 회원 403, 헤더 없음 401). 팀 목록 형식(PageResponse), 최신순. 출처 칸은 없다.
 */
@RestController
@RequiredArgsConstructor
public class ChildPurchaseController {

    public static final String CHILD_PURCHASES_PATH = OrderController.ORDERS_PATH + "/items/mine";

    private final ChildPurchaseService childPurchaseService;

    /**
     * size는 1~100.
     */
    @GetMapping(CHILD_PURCHASES_PATH)
    public PageResponse<ChildPurchaseResponse> getPurchases(
        @OrderChildCaller OrderChild child,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(childPurchaseService.getPurchases(child.childProfileId(), page, size)
            .map(ChildPurchaseResponse::from));
    }
}
