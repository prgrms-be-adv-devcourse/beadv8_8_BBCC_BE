package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 카테고리에서 생기는 업무 오류다.
 * domain.product가 domain.type을 참조하므로, 반대 방향 참조를 만들지 않으려고 ProductErrorCode와 따로 둔다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {

    NOT_LEAF_CATEGORY(HttpStatus.BAD_REQUEST, "상품을 등록할 수 없는 카테고리입니다", "가장 아래 단계의 카테고리를 골라 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
