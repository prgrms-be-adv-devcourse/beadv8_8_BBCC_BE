package com.bbcc.kidly.boundedcontext.product.app.inventory.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 재고 조정 요청의 증감량이 0이 아니고 사유에 맞는 부호인지 검사한다 (INV-18).
 */
@Documented
@Constraint(validatedBy = InventoryAdjustmentSignValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InventoryAdjustmentSign {

    String message() default "증감량이 사유에 맞지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
