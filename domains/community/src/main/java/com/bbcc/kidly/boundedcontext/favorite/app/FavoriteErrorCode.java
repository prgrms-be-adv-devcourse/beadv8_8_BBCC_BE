package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 찜 컨텍스트 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum FavoriteErrorCode implements ErrorCode {

    INVALID_PRODUCT_ID(HttpStatus.BAD_REQUEST, "잘못된 상품 ID입니다", "productId는 상품의 ID(양의 정수)로 보내 주세요."),
    INVALID_PRODUCT_IDS(HttpStatus.BAD_REQUEST, "잘못된 상품 ID 목록입니다",
        "productIds는 쉼표로 구분한 상품 ID 1~50개로 보내 주세요. cursor와 함께 보낼 수 없습니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "잘못된 페이지 크기입니다", "size는 1 이상 50 이하 정수로 보내 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다",
        "cursor는 직전 응답의 nextCursor를 그대로 보내 주세요. 처음부터 읽으려면 cursor를 빼고 보내 주세요."),
    FAVORITE_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "찜할 수 없는 상품입니다",
        "없거나 아직 판매를 시작하지 않은 상품입니다. 상품 목록에서 다시 골라 주세요."),
    FAVORITE_PRODUCT_SALE_ENDED(HttpStatus.CONFLICT, "판매가 끝난 상품입니다", "판매가 끝난 상품은 새로 찜할 수 없습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
