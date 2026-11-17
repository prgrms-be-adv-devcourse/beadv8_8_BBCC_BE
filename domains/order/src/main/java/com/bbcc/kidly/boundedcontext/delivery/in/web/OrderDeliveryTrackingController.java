package com.bbcc.kidly.boundedcontext.delivery.in.web;

import com.bbcc.kidly.boundedcontext.delivery.app.TrackingHistoryService;
import com.bbcc.kidly.boundedcontext.delivery.in.web.dto.DeliveryTrackingResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 택배 진행 이력(#1320). 배송 자원이라 배송 컨텍스트에 두고, 경로는 주문 아래(주문 상세에서 본다).
 */
@RestController
@RequiredArgsConstructor
public class OrderDeliveryTrackingController {

    public static final String TRACKING_PATH = "/api/v1/orders/{orderId}/deliveries/{sellerOrderId}/tracking";

    private final TrackingHistoryService trackingHistoryService;

    /**
     * 그 주문 구매자만. sellerOrderId는 판매자 API와 같은 이름의 배송 ID다. 남의 주문 · 없는 배송 404, 운송장 형식 오류 409,
     * 조회 꺼짐 · 외부 실패 503. 발송 전 · 조회할 수 없는 택배사(DUMMY)는 200 · events [](발송 전이면 carrier · trackingNumber null).
     */
    @GetMapping(TRACKING_PATH)
    public DeliveryTrackingResponse getTracking(@LoginMember AuthMember member, @PathVariable Long orderId,
        @PathVariable Long sellerOrderId) {
        return DeliveryTrackingResponse.from(trackingHistoryService.getTracking(member.id(), orderId, sellerOrderId));
    }
}
