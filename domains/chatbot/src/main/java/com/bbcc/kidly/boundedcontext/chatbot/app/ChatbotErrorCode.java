package com.bbcc.kidly.boundedcontext.chatbot.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 챗봇 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ChatbotErrorCode implements ErrorCode {

    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "잘못된 문서 분류입니다", "category는 FAQ · POLICY · SIZE_GUIDE 중 하나로 보내 주세요."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다", "없거나 이미 지운 문서입니다. 목록에서 다시 골라 주세요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI를 쓸 수 없습니다", "AI 서비스가 응답하지 않습니다. 잠시 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
