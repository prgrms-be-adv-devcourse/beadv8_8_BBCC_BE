package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 상품 항목에서 생기는 업무 오류다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AttributeErrorCode implements ErrorCode {

    ATTRIBUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다", "항목을 다시 확인해 주세요."),
    ATTRIBUTE_VALUE_NOT_FOUND(HttpStatus.NOT_FOUND, "목록 값을 찾을 수 없습니다", "목록 값을 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
