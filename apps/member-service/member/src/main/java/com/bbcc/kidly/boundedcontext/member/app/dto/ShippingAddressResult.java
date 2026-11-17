package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;

public record ShippingAddressResult(
    Long id, String recipientName, String recipientPhone, String zipCode, String roadAddress,
    String detailAddress, boolean isDefault) {

    public static ShippingAddressResult of(ShippingAddress address, boolean isDefault) {
        return new ShippingAddressResult(
            address.getId(), address.getRecipientName(), address.getRecipientPhone(), address.getZipCode(),
            address.getRoadAddress(), address.getDetailAddress(), isDefault);
    }
}
