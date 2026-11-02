package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 리뷰 컨텍스트 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다(행동이 다르면 코드를 나눈다).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "잘못된 페이지 크기입니다", "size는 1 이상 50 이하 정수로 보내 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다",
        "cursor는 직전 응답의 nextCursor를 그대로 보내 주세요. 처음부터 읽으려면 cursor를 빼고 보내 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
