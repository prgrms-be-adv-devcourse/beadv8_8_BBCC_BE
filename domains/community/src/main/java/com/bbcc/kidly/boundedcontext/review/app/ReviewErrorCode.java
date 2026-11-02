package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 리뷰 컨텍스트 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다(행동이 다르면 코드를 나눈다).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "잘못된 페이지 크기입니다", "size는 1 이상 50 이하 정수로 보내 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다",
        "cursor는 직전 응답의 nextCursor를 그대로 보내 주세요. 처음부터 읽으려면 cursor를 빼고 보내 주세요."),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "잘못된 별점입니다", "별점은 1 이상 5 이하 정수로 보내 주세요."),
    INVALID_CONTENT(HttpStatus.BAD_REQUEST, "잘못된 리뷰 본문입니다", "본문은 공백을 빼고 1자 이상 1000자 이하로 써 주세요."),
    INVALID_PRODUCT_ID(HttpStatus.BAD_REQUEST, "잘못된 상품 ID입니다", "productId는 상품의 ID(양의 정수)로 보내 주세요."),
    INVALID_ORDER_ITEM_ID(HttpStatus.BAD_REQUEST, "잘못된 주문상품 ID입니다",
        "orderItemId는 리뷰 작성 가능 목록의 orderItemId를 그대로 보내 주세요."),
    REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "리뷰를 쓸 수 없는 주문상품입니다",
        "구매확정한 내 주문상품에만 리뷰를 쓸 수 있습니다. 리뷰 작성 가능 목록에서 골라 주세요."),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "내 리뷰가 아닙니다", "내가 쓴 리뷰만 고치거나 지울 수 있습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다", "없거나 지워진 리뷰입니다. 리뷰 목록을 새로고침해 주세요."),
    REVIEW_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 리뷰를 쓴 주문상품입니다",
        "주문상품마다 리뷰는 하나입니다. 내 리뷰를 고치려면 수정해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
