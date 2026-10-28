package com.bbcc.kidly.boundedcontext.product.in.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryAdjustmentService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.dto.InventoryAdjustmentRequest;
import com.bbcc.kidly.boundedcontext.product.in.inventory.dto.InventoryAdjustmentResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 재고 API. 로그인한 판매자 본인의 옵션만 다룬다.
 */
@RestController
@RequiredArgsConstructor
public class SellerInventoryController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping("/api/v1/seller/options/{optionId}/inventory-adjustments")
    public InventoryAdjustmentResponse adjust(
        @LoginMember AuthMember member,
        @PathVariable Long optionId,
        @Valid @RequestBody InventoryAdjustmentRequest request) {
        return InventoryAdjustmentResponse.from(inventoryAdjustmentService.adjust(member.id(), optionId, request));
    }
}
