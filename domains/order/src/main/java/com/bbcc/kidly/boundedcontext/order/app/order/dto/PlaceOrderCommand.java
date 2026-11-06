package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import java.util.List;

/**
 * 주문하기 입력. 장바구니를 읽지 않고 요청 값이 기준이다.
 */
public record PlaceOrderCommand(List<Item> items, ShippingAddress shippingAddress) {

    /**
     * @param childId 자녀를 고르지 않으면 null
     * @param unitPrice 화면이 보여 준 단가 (현재 단가와 다르면 409)
     * @param cartItemId 이 줄을 산 장바구니 칸 ID(#998, 선택). 주면 장바구니가 '구매자 칸 또는 구매자가 허용된 예약 칸'인지 확인하고,
     *     결제가 끝나면 그 칸을 지운다. 없으면 null(결제 뒤 구매자 회원의 같은 (옵션, 자녀) 칸을 지운다)
     */
    public record Item(Long optionId, Long childId, int quantity, long unitPrice, Long cartItemId) {

        public Item(Long optionId, Long childId, int quantity, long unitPrice) {
            this(optionId, childId, quantity, unitPrice, null);
        }
    }

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }
}
