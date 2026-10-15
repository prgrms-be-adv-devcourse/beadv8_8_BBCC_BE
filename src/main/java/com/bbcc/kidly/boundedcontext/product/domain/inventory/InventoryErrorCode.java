package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode {

    DECREASE_OVER_AVAILABLE(HttpStatus.CONFLICT, "판매 가능 수량보다 많이 줄일 수 없습니다", "판매 가능 수량보다 많이 줄일 수 없습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
