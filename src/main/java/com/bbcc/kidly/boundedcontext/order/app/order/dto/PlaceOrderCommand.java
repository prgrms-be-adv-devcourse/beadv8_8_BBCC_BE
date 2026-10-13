package com.bbcc.kidly.boundedcontext.order.app.order.dto;

import java.util.List;

/**
 * 주문하기 입력. 장바구니를 읽지 않고 요청 값이 기준이다.
 */
public record PlaceOrderCommand(List<Item> items, ShippingAddress shippingAddress) {

    /**
     * @param childId 자녀를 고르지 않으면 null
     * @param unitPrice 화면이 보여 준 단가 (현재 단가와 다르면 409)
     */
    public record Item(Long optionId, Long childId, int quantity, long unitPrice) {
    }

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }
}
