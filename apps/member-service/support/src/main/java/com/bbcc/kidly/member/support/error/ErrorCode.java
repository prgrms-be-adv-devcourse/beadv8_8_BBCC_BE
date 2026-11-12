package com.bbcc.kidly.member.support.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답(ProblemDetail)의 상태, 제목, 기본 설명, 오류 코드. 각 도메인은 이 인터페이스를 구현하는 enum을 만든다.
 */
public interface ErrorCode {

    HttpStatus status();

    String title();

    String detail();

    /**
     * 오류 응답의 {@code code}. 클라이언트가 문구 대신 이 값으로 분기한다. enum이 상수 이름(예: {@code PRICE_CHANGED})으로 구현한다.
     * 이미 내보낸 상수의 이름은 바꾸지 않는다.
     */
    String name();
}
