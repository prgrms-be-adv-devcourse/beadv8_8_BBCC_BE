package com.bbcc.kidly.boundedcontext.member.domain;

public record ShippingAddressInfo(
    String recipientName,
    String recipientPhone,
    String zipCode,
    String roadAddress,
    String detailAddress) {
}
