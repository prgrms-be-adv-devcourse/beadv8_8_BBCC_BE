package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 기준 옷 값이 규칙에 맞지 않을 때의 오류다. 보호자가 입력을 고치면 되므로 모두 400이다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ReferenceGarmentErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "확인할 수 없는 세부 종류입니다",
        "기준 옷의 세부 종류를 상품 카테고리에서 다시 골라 주세요."),
    CATEGORY_PART_MISMATCH(HttpStatus.BAD_REQUEST, "구분과 맞지 않는 세부 종류입니다",
        "상의 기준 옷은 상의 · 아우터 · 원피스, 하의 기준 옷은 하의 · 치마 카테고리에서 골라 주세요."),
    REQUIRED_MEASUREMENT_MISSING(HttpStatus.BAD_REQUEST, "필수 실측이 없습니다",
        "상의는 총장과 가슴단면, 하의는 총장과 허리단면을 입력해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
