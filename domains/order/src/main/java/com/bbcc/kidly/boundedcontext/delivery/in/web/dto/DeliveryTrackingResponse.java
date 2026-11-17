package com.bbcc.kidly.boundedcontext.delivery.in.web.dto;

import com.bbcc.kidly.boundedcontext.delivery.app.dto.DeliveryTrackingView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매자 택배 진행 이력 응답(#1320). ID는 문자열, 이벤트는 최신 먼저(시각 · 장소 이름 · 상태 코드만).
 */
public record DeliveryTrackingResponse(String deliveryId, String carrier, String trackingNumber, List<Event> events) {

    /**
     * @param time 서버 시간대 시각(없으면 null)
     * @param location 장소 이름(없으면 null)
     * @param status 택배 조회 상태 코드(IN_TRANSIT · OUT_FOR_DELIVERY · DELIVERED 등)
     */
    public record Event(LocalDateTime time, String location, String status) {
    }

    public static DeliveryTrackingResponse from(DeliveryTrackingView view) {
        return new DeliveryTrackingResponse(String.valueOf(view.deliveryId()), view.carrier(),
            view.trackingNumber(),
            view.events().stream().map(event -> new Event(event.time(), event.location(), event.status())).toList());
    }
}
