package com.bbcc.kidly.boundedcontext.delivery.in.web;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryService;
import com.bbcc.kidly.boundedcontext.delivery.in.web.dto.CancelSellerOrderRequest;
import com.bbcc.kidly.boundedcontext.delivery.in.web.dto.DeliveryResponse;
import com.bbcc.kidly.boundedcontext.delivery.in.web.dto.SellerOrderResponse;
import com.bbcc.kidly.boundedcontext.delivery.in.web.dto.ShipRequest;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 주문(배송) API. 판매자 본인의 배송만 다룬다. SELLER 권한은 Gateway가 확인한다.
 */
@RestController
@RequestMapping(SellerOrderController.SELLER_ORDERS_PATH)
@RequiredArgsConstructor
public class SellerOrderController {

    public static final String SELLER_ORDERS_PATH = "/api/v1/seller/orders";

    private final DeliveryService deliveryService;

    /**
     * 최근 것부터. size는 1~100.
     */
    @GetMapping
    public PageResponse<SellerOrderResponse> getSellerOrders(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            deliveryService.getSellerOrders(member.id(), page, size).map(SellerOrderResponse::from));
    }

    @PostMapping("/{sellerOrderId}/ship")
    public DeliveryResponse ship(
        @LoginMember AuthMember member,
        @PathVariable Long sellerOrderId,
        @Valid @RequestBody ShipRequest request) {
        return DeliveryResponse.from(
            deliveryService.ship(member.id(), sellerOrderId, request.carrier(), request.trackingNumber()));
    }

    /**
     * 배송 완료와 주문상품 배송 기록을 서비스 메서드 하나(한 트랜잭션)에 맡긴다. 이미 완료여도 200.
     */
    @PostMapping("/{sellerOrderId}/deliver")
    public DeliveryResponse deliver(@LoginMember AuthMember member, @PathVariable Long sellerOrderId) {
        return DeliveryResponse.from(deliveryService.complete(member.id(), sellerOrderId));
    }

    /**
     * 발송 전 자기 몫 취소(#655). 배송 취소 · 주문상품 취소 · 이벤트를 서비스 메서드 하나(한 트랜잭션)에 맡긴다.
     * 스위치(kidly.order.partial-cancel.enabled)가 꺼져 있으면 409.
     */
    @PostMapping("/{sellerOrderId}/cancel")
    public DeliveryResponse cancel(
        @LoginMember AuthMember member,
        @PathVariable Long sellerOrderId,
        @Valid @RequestBody CancelSellerOrderRequest request) {
        return DeliveryResponse.from(
            deliveryService.cancel(member.id(), sellerOrderId, request.reason(), request.memo()));
    }
}
