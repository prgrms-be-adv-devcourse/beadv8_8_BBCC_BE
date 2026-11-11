package com.bbcc.kidly.boundedcontext.order.out.ai;

/**
 * AI 진단 호출 실패(#926). 메시지에는 이유 코드만 넣고 요청 · 응답 본문 · 키는 넣지 않는다.
 */
class AiDiagnosisException extends RuntimeException {

    static final String TIMEOUT = "TIMEOUT";
    static final String HTTP_ERROR = "HTTP_ERROR";
    static final String BAD_RESPONSE = "BAD_RESPONSE";
    static final String ERROR = "ERROR";

    private final String reason;

    AiDiagnosisException(String reason, String detail) {
        super(reason + (detail == null ? "" : ":" + detail));
        this.reason = reason;
    }

    static AiDiagnosisException badResponse(String detail) {
        return new AiDiagnosisException(BAD_RESPONSE, detail);
    }

    String reason() {
        return reason;
    }
}
