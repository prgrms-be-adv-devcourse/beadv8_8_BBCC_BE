package com.bbcc.kidly.boundedcontext.product.domain.product;

import java.util.List;

/**
 * 상품 썸네일과 이미지 목록이 판매자 본인이 업로드 주소로 올린 이미지인지 검사한다.
 * 본인 경로(ownPrefix) 아래에 서버가 만든 이름({UUID}.{확장자})으로 올린 이미지만 받는다.
 */
public final class ProductImageUrls {

    private ProductImageUrls() {
    }

    public static void validate(String thumbnailUrl, List<String> imageUrls, String ownPrefix) {
    }
}
