package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * 부호 기준은 InventoryAdjustmentReason.allows 하나뿐이고, 이 검증기는 그것을 부르기만 한다.
 */
public class InventoryAdjustmentSignValidator
    implements ConstraintValidator<InventoryAdjustmentSign, InventoryAdjustmentRequest> {

    private static final Set<String> SELLER_REASONS = Set.of("RESTOCK", "DEFECTIVE", "LOST", "CORRECTION");

    @Override
    public boolean isValid(InventoryAdjustmentRequest request, ConstraintValidatorContext context) {
        Integer deltaQuantity = request.deltaQuantity();
        String reason = request.reason();
        // 값이 없거나 판매자가 쓸 수 없는 사유면 칸 단위 검증이 알리므로 건너뛴다
        if (deltaQuantity == null || reason == null || !SELLER_REASONS.contains(reason)) {
            return true;
        }
        if (deltaQuantity == 0) {
            return reject(context, "증감량은 0이 아니어야 합니다.");
        }
        if (InventoryAdjustmentReason.valueOf(reason).allows(deltaQuantity)) {
            return true;
        }
        String required = deltaQuantity < 0 ? "커야" : "작아야";
        return reject(context, reason + " 사유는 증감량이 0보다 " + required + " 합니다.");
    }

    // 메시지에 사용자 값이 들어가면 EL 보간이 일어날 수 있다. 사유 이름은 허용된 네 값뿐이라 enum 이름만 넣어 안전하다
    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode("deltaQuantity")
            .addConstraintViolation();
        return false;
    }
}
