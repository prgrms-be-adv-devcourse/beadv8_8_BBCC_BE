package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    MULTIPLE_VALUES_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "값을 하나만 고를 수 있는 항목입니다", "이 항목은 값을 하나만 고를 수 있습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
