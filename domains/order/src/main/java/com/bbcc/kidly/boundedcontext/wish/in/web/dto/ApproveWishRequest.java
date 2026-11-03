package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import com.bbcc.kidly.boundedcontext.wish.app.dto.ApproveWishCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * POST /wish/requests/{requestId}/approval(#701 · 경로 #718). 고른 줄만 고른 수량으로 승인한다.
 *
 * @param checkout ORDER(바로 결제) · CART(보호자 장바구니로 옮기기). 다른 값은 400
 * @param shippingAddress 바로 결제의 배송지(바로 결제면 필수). 장바구니로 옮기기면 보내지 않는다
 */
public record ApproveWishRequest(
    @NotNull String checkout,
    @NotEmpty List<@Valid @NotNull Item> items,
    @Valid ShippingAddress shippingAddress) {

    /**
     * @param unitPrice 화면이 보여 준 지금 가격. 바로 결제면 필수(지금 가격과 다르면 409, 주문하기와 같음)
     */
    public record Item(
        @NotNull Long wishItemId,
        @NotNull @Min(1) @Max(99) Integer quantity,
        @PositiveOrZero Long unitPrice) {
    }

    public record ShippingAddress(
        @NotBlank String recipientName,
        @NotBlank String phone,
        @NotBlank String zipCode,
        @NotBlank String address) {
    }

    public ApproveWishCommand toCommand() {
        return new ApproveWishCommand(checkout,
            items.stream().map(item -> new ApproveWishCommand.Item(item.wishItemId(), item.quantity(),
                item.unitPrice())).toList(),
            shippingAddress == null ? null : new ApproveWishCommand.ShippingAddress(shippingAddress.recipientName(),
                shippingAddress.phone(), shippingAddress.zipCode(), shippingAddress.address()));
    }
}
