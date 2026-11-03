package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.global.error.BusinessException;

/**
 * 요청의 상품 ID(문자열)를 양의 정수로 읽는다. 아니면 INVALID_PRODUCT_ID.
 */
final class FavoriteIds {

    private FavoriteIds() {
    }

    static Long productId(String productId) {
        try {
            long value = Long.parseLong(productId);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // 아래에서 같은 오류로 응답한다(null 포함)
        }
        throw new BusinessException(FavoriteErrorCode.INVALID_PRODUCT_ID);
    }
}
