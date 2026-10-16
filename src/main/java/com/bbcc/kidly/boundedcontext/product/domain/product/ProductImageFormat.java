package com.bbcc.kidly.boundedcontext.product.domain.product;

public enum ProductImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ProductImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    // 업로드 주소에 서명한 Content-Type과 업로드 요청의 헤더가 글자까지 같아야 하므로 대소문자를 바꾸지 않고 비교한다
    public static ProductImageFormat fromContentType(String contentType) {
        for (ProductImageFormat format : values()) {
            if (format.contentType.equals(contentType)) {
                return format;
            }
        }
        throw new IllegalArgumentException("허용하지 않는 이미지 형식입니다");
    }
}
