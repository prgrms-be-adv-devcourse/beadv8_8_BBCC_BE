package com.bbcc.kidly.boundedcontext.product.app.port.product;

import com.bbcc.kidly.shared.product.domain.ProductStatus;

public record SellerProductFilter(ProductStatus status, boolean lowStock) {

    public static SellerProductFilter all() {
        return new SellerProductFilter(null, false);
    }
}
