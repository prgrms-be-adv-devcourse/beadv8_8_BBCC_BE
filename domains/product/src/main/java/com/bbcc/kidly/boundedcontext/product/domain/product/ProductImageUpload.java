package com.bbcc.kidly.boundedcontext.product.domain.product;

import java.util.UUID;

public record ProductImageUpload(ProductImageFormat format, long contentLength) {

    public static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;

    public static ProductImageUpload of(String contentType, long contentLength) {
        ProductImageFormat format = ProductImageFormat.fromContentType(contentType);
        return new ProductImageUpload(format, contentLength);
    }

    public String key(String environment, long sellerId, UUID objectId) {
        return keyDirectory(environment, sellerId) + objectId + "." + format.extension();
    }

    public static String keyDirectory(String environment, long sellerId) {
        // dev와 prod가 한 버킷을 경로로만 나눠 쓰므로, 환경 값이 경로를 바꾸지 못하게 막는다
        if (environment.isBlank() || environment.contains("/")) {
            throw new IllegalArgumentException("환경 값은 비어 있거나 /를 담을 수 없습니다");
        }
        if (sellerId <= 0) {
            throw new IllegalArgumentException("sellerId는 0보다 커야 합니다");
        }
        return environment + "/products/" + sellerId + "/";
    }
}
