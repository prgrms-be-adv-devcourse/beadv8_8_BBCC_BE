package com.bbcc.kidly.boundedcontext.product.domain.image;

import java.util.UUID;

public record ProductImageUpload(ProductImageType type, long contentLength) {

    public static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;

    public static ProductImageUpload of(String contentType, long contentLength) {
        if (contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("이미지 크기는 1바이트 이상 10MB 이하여야 합니다");
        }
        return new ProductImageUpload(ProductImageType.fromContentType(contentType), contentLength);
    }

    public String key(String environment, long sellerId, UUID id) {
        return environment + "/products/" + sellerId + "/" + id + "." + type.extension();
    }
}
