package com.bbcc.kidly.boundedcontext.delivery.out.tracker;

/**
 * Delivery Tracker 호출 실패. code는 GraphQL errors[0].extensions.code(UNAUTHENTICATED · FORBIDDEN · BAD_REQUEST ·
 * NOT_FOUND · INTERNAL) 또는 클라이언트 쪽 코드(DISABLED · HTTP_{상태} · NETWORK · INVALID_RESPONSE).
 */
class DeliveryTrackerException extends RuntimeException {

    private final String code;

    DeliveryTrackerException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    DeliveryTrackerException(String code, String message, Throwable cause) {
        super(code + ": " + message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }

    /**
     * 운송장 번호 형식 오류. 다시 조회해도 결과가 같다. 그 밖의 코드는 다음 주기에 다시 조회한다.
     */
    boolean isInvalidTrackingNumber() {
        return "BAD_REQUEST".equals(code);
    }
}
