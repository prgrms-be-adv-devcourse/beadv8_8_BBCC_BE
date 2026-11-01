package com.bbcc.kidly.boundedcontext.delivery.app.port;

import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import java.time.LocalDateTime;

/**
 * 택배 배송 상태 조회(#650). 구현은 out/tracker(Delivery Tracker). 외부 오류는 예외 대신 UNAVAILABLE 결과로 돌려준다.
 */
public interface DeliveryStatusChecker {

    /**
     * 인증값이 없으면 false. 조회 스케줄러는 아무것도 하지 않는다(판매자 수동 배송 완료는 그대로).
     */
    boolean isEnabled();

    TrackingResult check(Delivery.Carrier carrier, String trackingNumber);

    /**
     * 택배사에 배송 상태를 조회한 결과. 외부 오류는 예외가 아니라 UNAVAILABLE로 담는다.
     * 조회 결과는 기술 장치의 값이라 domain이 아니라 이 포트 안에 둔다(#642).
     *
     * @param kind 결과 종류
     * @param deliveredAt DELIVERED일 때만 택배사가 알려 준 배송 완료 시각(서버 시간대), 나머지는 null
     * @param reason 실패 이유 코드(로그용, 공백 없음). 정상이면 null
     */
    record TrackingResult(Kind kind, LocalDateTime deliveredAt, String reason) {

        public static TrackingResult delivered(LocalDateTime deliveredAt) {
            return new TrackingResult(Kind.DELIVERED, deliveredAt, null);
        }

        public static TrackingResult inProgress() {
            return new TrackingResult(Kind.IN_PROGRESS, null, null);
        }

        public static TrackingResult invalidTrackingNumber(String reason) {
            return new TrackingResult(Kind.INVALID_TRACKING_NUMBER, null, reason);
        }

        public static TrackingResult unavailable(String reason) {
            return new TrackingResult(Kind.UNAVAILABLE, null, reason);
        }
    }

    /**
     * 조회 결과 종류.
     */
    enum Kind {
        /** 택배사가 배송 완료라고 답했다 */
        DELIVERED,
        /** 아직 배송 중이거나 이벤트가 없다 */
        IN_PROGRESS,
        /** 운송장 번호 형식 오류. 다시 조회해도 결과가 같아 조회를 멈춘다 */
        INVALID_TRACKING_NUMBER,
        /** 일시 오류(없는 번호 · 호출 한도 · 인증 · 네트워크 · 응답 이상). 다음 주기에 다시 조회한다 */
        UNAVAILABLE
    }
}
