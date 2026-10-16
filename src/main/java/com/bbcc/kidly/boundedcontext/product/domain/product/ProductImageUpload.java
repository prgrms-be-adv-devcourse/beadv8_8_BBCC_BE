package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

public record ProductImageUpload(ProductImageFormat format, long contentLength) {

    public static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;

    public static ProductImageUpload of(String contentType, long contentLength) {
        if (contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("이미지 크기는 1바이트 이상 10MB 이하여야 합니다");
        }
        ProductImageFormat format = ProductImageFormat.fromContentType(
            requireNonNull(contentType, "contentType은 필수입니다"));
        return new ProductImageUpload(format, contentLength);
    }

    public String key(String environment, long sellerId, UUID objectId) {
        return requireNonNull(environment, "environment는 필수입니다") + "/products/" + sellerId + "/"
            + requireNonNull(objectId, "objectId는 필수입니다") + "." + format.extension();
    }
}
