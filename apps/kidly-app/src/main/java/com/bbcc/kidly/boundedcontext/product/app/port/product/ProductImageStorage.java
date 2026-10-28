package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;

/**
 * 상품 이미지를 올리고 보여 주는 저장소(S3)의 요청서다. 업로드 주소를 발급하고, 판매자가 올린 이미지가 놓이는 공개 주소 접두어를 알려 준다.
 */
public interface ProductImageStorage {

    ProductImageUploadUrl issueUploadUrl(ProductImageUpload upload, long sellerId);

    /** 판매자가 올린 이미지가 놓이는 공개 주소의 접두어다. /로 끝난다. */
    String ownImageUrlPrefix(long sellerId);
}
