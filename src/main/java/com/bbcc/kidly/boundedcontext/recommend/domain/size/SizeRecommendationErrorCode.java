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

    // 화면은 두 404를 detail로 구분하므로, detail을 바꿀 때는 화면 담당과 함께 고친다
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다", "상품을 찾을 수 없습니다."),
    // 없는 자녀, 다른 회원의 자녀, 삭제된 자녀를 같은 문구로 알려 다른 회원의 자녀가 있는지 드러내지 않는다
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "자녀를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
