package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

public record ProductImageUpload(ProductImageFormat format, long contentLength) {

    public static final long MAX_CONTENT_LENGTH = 10L * 1024 * 1024;

    // 생성자를 직접 불러도 형식과 크기를 검사하도록 검증을 여기에 둔다
    public ProductImageUpload {
        requireNonNull(format, "format은 필수입니다");
        if (contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("이미지 크기는 1바이트 이상 10MB 이하여야 합니다");
        }
    }

    public static ProductImageUpload of(String contentType, long contentLength) {
        ProductImageFormat format = ProductImageFormat.fromContentType(
            requireNonNull(contentType, "contentType은 필수입니다"));
        return new ProductImageUpload(format, contentLength);
    }

    public String key(String environment, long sellerId, UUID objectId) {
        requireNonNull(environment, "environment는 필수입니다");
        requireNonNull(objectId, "objectId는 필수입니다");
        // dev와 prod가 한 버킷을 경로로만 나눠 쓰므로, 환경 값이 경로를 바꾸지 못하게 막는다
        if (environment.isBlank() || environment.contains("/")) {
            throw new IllegalArgumentException("환경 값은 비어 있거나 /를 담을 수 없습니다");
        }
        if (sellerId <= 0) {
            throw new IllegalArgumentException("sellerId는 0보다 커야 합니다");
        }
        return environment + "/products/" + sellerId + "/" + objectId + "." + format.extension();
    }
}
