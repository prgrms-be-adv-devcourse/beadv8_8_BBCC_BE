package com.bbcc.kidly.global.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답(ProblemDetail)의 상태, 제목, 기본 설명. 각 도메인은 이 인터페이스를 구현하는 enum을 만든다.
 */
public interface ErrorCode {

    HttpStatus status();

    String title();

    String detail();
}
