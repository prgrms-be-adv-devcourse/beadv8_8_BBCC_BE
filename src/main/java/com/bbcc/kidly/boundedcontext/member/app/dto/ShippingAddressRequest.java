package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 등록 · 수정 입력. 수정도 모든 값을 받는 전체 교체다.
 *
 * @param isDefault true면 기본 배송지로 지정한다. false는 기본 배송지를 바꾸지 않는다
 */
public record ShippingAddressRequest(
    @NotBlank @Size(max = ShippingAddress.RECIPIENT_NAME_MAX_LENGTH) String recipientName,
    @NotNull @Pattern(regexp = ShippingAddress.RECIPIENT_PHONE_REGEX, message = "010-1234-5678 형식이어야 합니다")
    String recipientPhone,
    @NotBlank @Size(max = ShippingAddress.ZIP_CODE_MAX_LENGTH) String zipCode,
    @NotBlank @Size(max = ShippingAddress.ROAD_ADDRESS_MAX_LENGTH) String roadAddress,
    @Size(max = ShippingAddress.DETAIL_ADDRESS_MAX_LENGTH) String detailAddress,
    boolean isDefault) {

    public ShippingAddressInfo toInfo() {
        return new ShippingAddressInfo(recipientName, recipientPhone, zipCode, roadAddress, detailAddress);
    }
}
