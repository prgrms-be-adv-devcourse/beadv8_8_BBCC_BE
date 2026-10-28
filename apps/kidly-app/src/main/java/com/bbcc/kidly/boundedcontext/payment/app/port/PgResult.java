package com.bbcc.kidly.boundedcontext.payment.app.port;

/**
 * PG 호출 결과. 모름(Unknown)은 실패가 아니다. 돈이 빠져나갔을 수 있으므로 조회로 확정한다.
 */
public sealed interface PgResult<T> {

    record Success<T>(T value) implements PgResult<T> {
    }

    // code는 PG 오류 코드 또는 승인되지 않고 끝난 결제의 상태
    record Rejected<T>(String code, String message) implements PgResult<T> {
    }

    record Unknown<T>(String reason) implements PgResult<T> {
    }
}
