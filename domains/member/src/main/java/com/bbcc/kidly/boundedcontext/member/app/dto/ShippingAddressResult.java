package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;

/**
 * 배송지 조회 결과. 컨트롤러는 엔티티 대신 이것으로 응답을 만든다.
 *
 * @param detailAddress 없으면 null
 * @param isDefault 기본 배송지와 비교해 계산한 값 (저장하지 않음)
 */
public record ShippingAddressResult(
    Long id, String recipientName, String recipientPhone, String zipCode, String roadAddress,
    String detailAddress, boolean isDefault) {

    public static ShippingAddressResult of(ShippingAddress address, boolean isDefault) {
        return new ShippingAddressResult(
            address.getId(), address.getRecipientName(), address.getRecipientPhone(), address.getZipCode(),
            address.getRoadAddress(), address.getDetailAddress(), isDefault);
    }
}
