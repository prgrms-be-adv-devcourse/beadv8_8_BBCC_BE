package com.bbcc.kidly.boundedcontext.delivery.app.dto;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryStatusChecker.TrackingEvent;
import java.util.List;

/**
 * 구매자가 보는 배송 하나의 택배 진행 이력(#1320). 이벤트는 최신 먼저, 시각 · 장소 이름 · 상태 코드만.
 * carrier는 택배사 코드 이름(CJ · HANJIN 등, in이 domain을 보지 않게 문자열).
 */
public record DeliveryTrackingView(Long deliveryId, String carrier, String trackingNumber,
    List<TrackingEvent> events) {

    public DeliveryTrackingView {
        events = List.copyOf(events);
    }
}
