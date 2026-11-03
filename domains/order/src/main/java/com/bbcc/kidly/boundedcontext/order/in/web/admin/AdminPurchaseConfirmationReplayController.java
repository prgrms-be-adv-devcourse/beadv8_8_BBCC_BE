package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import com.bbcc.kidly.boundedcontext.order.app.order.PurchaseConfirmationRepublishService;
import com.bbcc.kidly.boundedcontext.order.in.web.admin.dto.PurchaseConfirmationReplayResponse;
import com.bbcc.kidly.global.security.AuthAdmin;
import com.bbcc.kidly.global.security.LoginAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 특정 수신 도메인에 의존하지 않는 주문 소유 구매확정 사실 복구 API.
 */
@RestController
@RequestMapping(AdminOrderController.ADMIN_ORDERS_PATH + "/purchase-confirmed-events")
@RequiredArgsConstructor
public class AdminPurchaseConfirmationReplayController {

    private final PurchaseConfirmationRepublishService service;

    @PostMapping
    public PurchaseConfirmationReplayResponse republish(
        @RequestParam(defaultValue = "0") @Min(0) long afterOrderItemId,
        @RequestParam(required = false) @Min(0) Long throughOrderItemId,
        @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size,
        @LoginAdmin AuthAdmin admin) {
        return PurchaseConfirmationReplayResponse.from(
            service.republish(afterOrderItemId, throughOrderItemId, size, admin.id()));
    }
}
