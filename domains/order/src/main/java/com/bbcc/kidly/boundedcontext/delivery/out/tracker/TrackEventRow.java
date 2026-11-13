package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

import java.time.OffsetDateTime;

/**
 * Delivery Tracker 진행 이벤트 하나(#1320). 요청한 칸(시각 · 상태 코드 · 장소 이름)만 담는다.
 *
 * @param time 택배사가 준 오프셋 그대로. 없거나 형식이 틀리면 null
 * @param location location.name. 없으면 null
 * @param statusCode status.code. 없으면 UNKNOWN
 */
record TrackEventRow(OffsetDateTime time, String location, String statusCode) {
}
