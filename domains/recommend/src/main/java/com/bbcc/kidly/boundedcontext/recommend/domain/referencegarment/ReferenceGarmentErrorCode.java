package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ReferenceGarmentErrorCode implements ErrorCode {

    UNKNOWN_PART(HttpStatus.BAD_REQUEST, "알 수 없는 기준 옷 구분입니다",
        "기준 옷 구분은 TOP · BOTTOM · OUTER · DRESS · SKIRT 중 하나입니다."),
    CHILD_FORBIDDEN(HttpStatus.FORBIDDEN, "자녀에 접근할 수 없습니다", "로그인한 보호자의 자녀만 다룰 수 있습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "확인할 수 없는 세부 종류입니다",
        "기준 옷의 세부 종류를 상품 카테고리에서 다시 골라 주세요."),
    CATEGORY_PART_MISMATCH(HttpStatus.BAD_REQUEST, "구분과 맞지 않는 세부 종류입니다",
        "상의는 상의, 바지는 하의 · 바지, 아우터는 아우터, 원피스는 원피스, 치마는 치마 카테고리에서 골라 주세요."),
    REQUIRED_MEASUREMENT_MISSING(HttpStatus.BAD_REQUEST, "필수 실측이 없습니다",
        "상의 · 아우터 · 원피스는 총장과 가슴단면, 바지 · 치마는 총장과 허리단면을 입력해 주세요."),
    UNSUPPORTED_MEASUREMENT(HttpStatus.BAD_REQUEST, "이 구분에 없는 실측 항목입니다",
        "상의 · 아우터는 총장 · 가슴단면 · 어깨너비 · 소매길이, 원피스는 총장 · 가슴단면 · 어깨너비, "
            + "바지는 총장 · 허리단면 · 밑위, 치마는 총장 · 허리단면 · 엉덩이단면만 입력할 수 있습니다."),
    INVALID_MEASUREMENT_VALUE(HttpStatus.BAD_REQUEST, "잘못된 실측 값입니다",
        "실측 값은 0cm보다 크고 200cm 이하이며, 소수 첫째 자리까지 입력할 수 있습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
