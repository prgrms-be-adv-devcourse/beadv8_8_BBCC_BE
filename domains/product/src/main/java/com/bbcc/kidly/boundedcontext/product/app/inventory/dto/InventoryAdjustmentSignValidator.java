package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public class InventoryAdjustmentSignValidator
    implements ConstraintValidator<InventoryAdjustmentSign, InventoryAdjustmentRequest> {

    private static final Set<String> SELLER_REASONS = Set.of("RESTOCK", "DEFECTIVE", "LOST", "CORRECTION");

    @Override
    public boolean isValid(InventoryAdjustmentRequest request, ConstraintValidatorContext context) {
        Integer deltaQuantity = request.deltaQuantity();
        String reason = request.reason();
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

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode("deltaQuantity")
            .addConstraintViolation();
        return false;
    }
}
