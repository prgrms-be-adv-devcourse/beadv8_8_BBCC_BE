package com.bbcc.kidly.boundedcontext.product.domain.image;

public enum ProductImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ProductImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static ProductImageType fromContentType(String contentType) {
        for (ProductImageType type : values()) {
            if (type.contentType.equals(contentType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("허용하지 않는 이미지 형식입니다");
    }
}
