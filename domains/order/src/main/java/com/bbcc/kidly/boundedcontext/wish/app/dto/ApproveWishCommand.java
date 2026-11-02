package com.bbcc.kidly.boundedcontext.wish.app.dto;

import java.util.List;

/**
 * 조르기 승인(#701).
 *
 * @param checkout ORDER(바로 결제) · CART(보호자 장바구니로 옮기기)
 * @param items 승인할 줄(일부만 · 수량을 바꿔도 된다)
 * @param shippingAddress 바로 결제의 배송지. 장바구니로 옮기기면 null
 */
public record ApproveWishCommand(String checkout, List<Item> items, ShippingAddress shippingAddress) {

    /**
     * @param unitPrice 보호자 화면이 보여 준 지금 가격. 바로 결제에 필요하다(장바구니로 옮기기면 null이어도 된다)
     */
    public record Item(Long wishItemId, int quantity, Long unitPrice) {
    }

    public record ShippingAddress(String recipientName, String phone, String zipCode, String address) {
    }
}
