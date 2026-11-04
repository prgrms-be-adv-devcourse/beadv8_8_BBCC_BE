package com.bbcc.kidly.boundedcontext.member.in.dto;

import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressResult;

/**
 * @param detailAddress 없으면 null
 * @param isDefault 기본 배송지인지
 */
public record ShippingAddressResponse(
    Long id, String recipientName, String recipientPhone, String zipCode, String roadAddress,
    String detailAddress, boolean isDefault) {

    public static ShippingAddressResponse from(ShippingAddressResult result) {
        return new ShippingAddressResponse(
            result.id(), result.recipientName(), result.recipientPhone(), result.zipCode(), result.roadAddress(),
            result.detailAddress(), result.isDefault());
    }
}
