package com.bbcc.kidly.boundedcontext.delivery.app.dto;

import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;

/**
 * 택배 조회 대상 한 건. 스케줄러(in)는 deliveryId만 쓴다.
 */
public record TrackingTarget(Long deliveryId, Delivery.Carrier carrier, String trackingNumber) {

    public static TrackingTarget from(Delivery delivery) {
        return new TrackingTarget(delivery.getId(), delivery.getCarrier(), delivery.getTrackingNumber());
    }
}
