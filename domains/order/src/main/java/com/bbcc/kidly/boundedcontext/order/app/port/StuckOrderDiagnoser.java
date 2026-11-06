package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 멈춘 주문 진단 요청(#926). 주문 쪽 값만 넘기고 요약 · 권장 조치 · 확신도를 받는다. 어떤 제공자 · 모델 · 요청 형식인지는
 * 구현(out/ai)만 안다. 넘기는 값에 개인정보(배송지 · 연락처 · 수령인 · 이름 · 닉네임 · 자녀 정보 · 회원 ID)를 넣지 않는다.
 * 실패 · 시간 초과 · 형식 오류 · 열림은 예외 대신 결과로 돌려준다.
 */
public interface StuckOrderDiagnoser {

    /**
     * 켜져 있고 쓸 준비가 됐는지(스위치 · 연결 설정). 아니면 부르지 않는다.
     */
    boolean isAvailable();

    Diagnosis diagnose(Request request);

    /**
     * @param stuckKind 멈춘 종류 이름(StuckOrderKind)
     * @param targetStatus 멈춘 대상의 기대 상태(예: PENDING_PAYMENT)
     * @param orderStatus 주문의 지금 상태(이력의 마지막 주문 상태, 모르면 null)
     * @param history 상태 이력(오래된 것부터, 최근 일부)
     * @param records 관련 실패 · 복구 · 아웃박스 기록 요약
     */
    record Request(String stuckKind, String targetType, String targetStatus, LocalDateTime since,
        String orderStatus, List<HistoryLine> history, List<RecordLine> records) {
    }

    /**
     * 상태 이력 한 줄. 주체 ID는 넣지 않는다.
     */
    record HistoryLine(String targetType, String fromStatus, String toStatus, String reason, String actorType,
        String source, LocalDateTime occurredAt) {
    }

    /**
     * @param kind EVENT_FAILURE · PAYMENT_RECOVERY · OUTBOX
     * @param type 이벤트 종류 등
     * @param code 실패 코드(없으면 null)
     */
    record RecordLine(String kind, String type, String status, String code, int attemptCount) {
    }

    enum Outcome {
        /** 요약 · 권장 조치 · 확신도를 받았다 */
        DIAGNOSED,
        /** 불렀지만 실패했다(시간 초과 · 응답 오류 · 형식 오류 등). 같은 상태로 다시 부르지 않는다 */
        FAILED,
        /** 부르지 않았다(꺼짐 · 서킷 열림). 다음 실행에서 다시 본다 */
        NOT_CALLED
    }

    enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * @param reason 실패 · 부르지 않은 이유 코드. 성공이면 null
     * @param provider 메모에 남길 답한 곳(구현이 정한 이름)
     * @param model 메모에 남길 모델 이름
     */
    record Diagnosis(Outcome outcome, String summary, String recommendedAction, Confidence confidence,
        String reason, String provider, String model) {

        public static Diagnosis diagnosed(String summary, String recommendedAction, Confidence confidence,
            String provider, String model) {
            return new Diagnosis(Outcome.DIAGNOSED, summary, recommendedAction, confidence, null, provider, model);
        }

        public static Diagnosis failed(String reason, String provider, String model) {
            return new Diagnosis(Outcome.FAILED, null, null, null, reason, provider, model);
        }

        public static Diagnosis notCalled(String reason) {
            return new Diagnosis(Outcome.NOT_CALLED, null, null, null, reason, null, null);
        }
    }
}
