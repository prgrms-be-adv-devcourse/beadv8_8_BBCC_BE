package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SizeRecommendationErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다", "상품을 다시 확인해 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "자녀를 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
