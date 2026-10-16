package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;

public interface ProductImageUploadUrlIssuer {

    ProductImageUploadUrl issue(ProductImageUpload upload, long sellerId);

    /** 판매자가 올린 이미지가 놓이는 공개 주소의 접두어다. /로 끝난다. */
    String ownImageUrlPrefix(long sellerId);
}
