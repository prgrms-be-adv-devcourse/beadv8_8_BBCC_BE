package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * PATCH /cart/items/{cartItemId}. 수량은 덮어쓴다.
 */
public record ChangeCartItemQuantityRequest(@Min(1) @Max(99) int quantity) {
}
