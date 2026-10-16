package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;

public interface ProductImageUploadUrlIssuer {

    ProductImageUploadUrl issue(ProductImageUpload upload, long sellerId);
}
