package com.bbcc.kidly.boundedcontext.product.domain.image;

import java.util.UUID;

public record ProductImageUpload(ProductImageType type, long contentLength) {

    public static ProductImageUpload of(String contentType, long contentLength) {
        return new ProductImageUpload(ProductImageType.fromContentType(contentType), contentLength);
    }

    public String key(String environment, long sellerId, UUID id) {
        return environment + "/products/" + sellerId + "/" + id + "." + type.extension();
    }
}
