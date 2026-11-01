package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import java.time.OffsetDateTime;

/**
 * Delivery Tracker 응답 그대로의 마지막 이벤트.
 *
 * @param statusCode lastEvent.status.code. 이벤트가 없으면 UNKNOWN
 * @param eventTime lastEvent.time. 택배사가 준 오프셋(Z · +09:00) 그대로. DELIVERED일 때만 있고 그 밖은 null
 */
record TrackResult(String statusCode, OffsetDateTime eventTime) {
}
