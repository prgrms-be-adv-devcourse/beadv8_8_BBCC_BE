package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 추천이 이벤트로 받아 둔 상품 · 자녀 복사본을 찾지 못했을 때의 오류다. 사이즈 추천과 자녀 맞춤 목록이 함께 쓴다.
 * 화면은 두 404를 detail로 구분하므로, detail을 바꿀 때는 화면 담당과 함께 고친다.
 * 없는 자녀, 다른 회원의 자녀, 삭제된 자녀는 같은 문구로 알려 다른 회원의 자녀가 있는지 드러내지 않는다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum RecommendErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다", "상품을 찾을 수 없습니다."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "자녀를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
