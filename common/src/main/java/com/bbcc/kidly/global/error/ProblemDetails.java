package com.bbcc.kidly.global.error;

import java.net.URI;
import org.springframework.http.ProblemDetail;

/**
 * ProblemDetail 5필드(type, title, status, detail, instance)를 채운다.
 */
public final class ProblemDetails {

    // Spring 7은 기본값 about:blank를 응답에서 빼므로 명시해서 5필드를 항상 보낸다
    public static final URI DEFAULT_TYPE = URI.create("about:blank");

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setType(DEFAULT_TYPE);
        problem.setTitle(errorCode.title());
        problem.setInstance(URI.create(instance));
        return problem;
    }

    public static ProblemDetail of(ErrorCode errorCode, String instance) {
        return of(errorCode, errorCode.detail(), instance);
    }
}
