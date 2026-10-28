package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 상품 항목에서 생기는 업무 오류다.
 * 상품 유형에 연결된 항목을 지우려는 요청은 항목의 현재 상태와 충돌하므로 409로 알린다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AttributeErrorCode implements ErrorCode {

    ATTRIBUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "항목을 찾을 수 없습니다", "항목을 다시 확인해 주세요."),
    ATTRIBUTE_VALUE_NOT_FOUND(HttpStatus.NOT_FOUND, "목록 값을 찾을 수 없습니다", "목록 값을 다시 확인해 주세요."),
    ATTRIBUTE_IN_USE(HttpStatus.CONFLICT, "지울 수 없는 항목입니다",
        "상품 유형에 연결된 항목은 지울 수 없습니다. 연결을 먼저 빼 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
