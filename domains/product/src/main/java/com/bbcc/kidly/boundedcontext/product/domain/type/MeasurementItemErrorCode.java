package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MeasurementItemErrorCode implements ErrorCode {

    MEASUREMENT_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "실측 항목을 찾을 수 없습니다", "실측 항목을 다시 확인해 주세요."),
    MEASUREMENT_ITEM_IN_USE(HttpStatus.CONFLICT, "지울 수 없는 실측 항목입니다",
        "카테고리가 필수로 연결한 실측 항목은 지울 수 없습니다. 카테고리에서 먼저 빼 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
