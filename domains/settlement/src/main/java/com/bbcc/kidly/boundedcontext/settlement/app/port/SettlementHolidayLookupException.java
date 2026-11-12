package com.bbcc.kidly.boundedcontext.settlement.app.port;

/**
 * 공휴일을 알 수 없다(그해 공휴일이 저장돼 있지 않거나 API 조회에 실패). 영업일인지 알 수 없으므로 호출한 쪽이 이번 실행을 건너뛴다.
 * API 요청 URL에 인증키가 들어 있어, 메시지에는 실패 종류와 결과 코드만 담고 원인 예외는 붙이지 않는다.
 */
public class SettlementHolidayLookupException extends RuntimeException {

    public SettlementHolidayLookupException(String message) {
        super(message);
    }
}
