package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 알림 도메인 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    // 남의 알림도 같은 404로 답해 있는지 없는지 알리지 않는다
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다", "없거나 내 알림이 아닙니다. 알림 목록을 새로고침해 주세요."),
    DEAD_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "DLT 메시지를 찾을 수 없습니다", "DLT 목록을 새로고침해 주세요."),
    INVALID_DEAD_LETTER_STATE(HttpStatus.BAD_REQUEST, "잘못된 DLT 상태입니다", "상태는 OPEN 또는 REPROCESSED입니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
