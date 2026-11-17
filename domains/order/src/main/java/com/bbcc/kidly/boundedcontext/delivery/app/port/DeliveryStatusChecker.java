package com.bbcc.kidly.boundedcontext.delivery.app.port;

import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import java.time.LocalDateTime;
import java.util.List;

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
     * 택배 진행 이력(#1320, 구매자 조회). 이벤트는 시각 · 장소 이름 · 상태 코드만, 최신 먼저, 최근 몇 개까지만 돌려준다.
     * 서킷은 상태 조회({@link #check})와 같은 설정의 별도 인스턴스이고, 호출 간격은 나눠 쓴다(구매자 요청은 자리를 최대 1초만 기다린다).
     * 외부 오류 · 서킷 열림 · 자리 없음은 예외 대신 UNAVAILABLE로 돌려준다.
     */
    TrackingHistory history(Delivery.Carrier carrier, String trackingNumber);

    /**
     * 택배 진행 이력 조회 결과(#1320).
     *
     * @param kind 결과 종류
     * @param events OK일 때 이벤트(최신 먼저, 택배사에 아직 등록 전이면 빈 목록), 그 밖은 빈 목록
     * @param reason 실패 이유 코드(로그용). 정상이면 null, 택배사 미등록 빈 이력이면 NOT_FOUND
     */
    record TrackingHistory(HistoryKind kind, List<TrackingEvent> events, String reason) {

        public static final String NOT_REGISTERED = "NOT_FOUND";

        public TrackingHistory {
            events = List.copyOf(events);
        }

        public static TrackingHistory ok(List<TrackingEvent> events) {
            return new TrackingHistory(HistoryKind.OK, events, null);
        }

        /**
         * 택배사에 아직 등록 전인 운송장(NOT_FOUND). 오류가 아니라 빈 이력이고, 곧 등록되므로 짧게만 캐시한다.
         */
        public static TrackingHistory notRegistered() {
            return new TrackingHistory(HistoryKind.OK, List.of(), NOT_REGISTERED);
        }

        public boolean isNotRegistered() {
            return kind == HistoryKind.OK && NOT_REGISTERED.equals(reason);
        }

        public static TrackingHistory unsupportedCarrier() {
            return new TrackingHistory(HistoryKind.UNSUPPORTED_CARRIER, List.of(), "UNSUPPORTED_CARRIER");
        }

        public static TrackingHistory invalidTrackingNumber(String reason) {
            return new TrackingHistory(HistoryKind.INVALID_TRACKING_NUMBER, List.of(), reason);
        }

        public static TrackingHistory unavailable(String reason) {
            return new TrackingHistory(HistoryKind.UNAVAILABLE, List.of(), reason);
        }
    }

    /**
     * 택배 진행 이벤트 하나(#1320). 설명 · 기사 · 연락처 등 그 밖의 칸은 두지 않는다.
     *
     * @param time 서버 시간대 시각(택배사가 주지 않았으면 null)
     * @param location 장소 이름(없으면 null)
     * @param status 택배 조회 상태 코드(IN_TRANSIT · OUT_FOR_DELIVERY · DELIVERED 등)
     */
    record TrackingEvent(LocalDateTime time, String location, String status) {
    }

    /**
     * 진행 이력 조회 결과 종류(#1320).
     */
    enum HistoryKind {
        /** 조회됨(이벤트가 없을 수 있다) */
        OK,
        /** 진행 이력을 조회할 수 없는 택배사(테스트용 DUMMY 등) */
        UNSUPPORTED_CARRIER,
        /** 택배사가 운송장 번호 형식이 틀렸다고 답했다. 다시 조회해도 같다 */
        INVALID_TRACKING_NUMBER,
        /** 일시 오류(꺼짐 · 호출 한도 · 인증 · 네트워크 · 응답 이상 · 서킷 열림) */
        UNAVAILABLE
    }

    /**
     * 택배사에 배송 상태를 조회한 결과. 외부 오류는 예외가 아니라 UNAVAILABLE로 담는다.
     * 조회 결과는 기술 장치의 값이라 domain이 아니라 이 포트 안에 둔다(#642).
     *
     * @param kind 결과 종류
     * @param deliveredAt DELIVERED일 때만 택배사가 알려 준 배송 완료 시각(서버 시간대), 나머지는 null
     * @param reason 실패 이유 코드(로그용, 공백 없음). 정상이면 null
     */
    record TrackingResult(Kind kind, LocalDateTime deliveredAt, String reason) {

        /**
         * 서킷브레이커가 열려 조회하지 않았다(#708). 다음 실행에서 다시 조회한다.
         */
        public static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";

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

        public static TrackingResult circuitOpen() {
            return unavailable(CIRCUIT_OPEN);
        }

        public boolean isCircuitOpen() {
            return kind == Kind.UNAVAILABLE && CIRCUIT_OPEN.equals(reason);
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
        /** 일시 오류(없는 번호 · 호출 한도 · 인증 · 네트워크 · 응답 이상 · 서킷 열림). 다음 주기에 다시 조회한다 */
        UNAVAILABLE
    }
}
