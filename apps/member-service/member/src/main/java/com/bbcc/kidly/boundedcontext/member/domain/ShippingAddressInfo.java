package com.bbcc.kidly.boundedcontext.member.domain;

/**
 * {@link ShippingAddress#register} · {@link ShippingAddress#update} 입력. 수정도 모든 값을 받는 전체 교체다.
 * 값만 담고, 검증은 엔티티가 한다.
 *
 * @param detailAddress 없으면 null. 공백이면 null로 저장한다
 */
public record ShippingAddressInfo(
    String recipientName,
    String recipientPhone,
    String zipCode,
    String roadAddress,
    String detailAddress) {
}
