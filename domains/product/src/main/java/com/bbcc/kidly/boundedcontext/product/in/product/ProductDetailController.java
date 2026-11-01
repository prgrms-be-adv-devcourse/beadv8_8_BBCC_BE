package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductDetailService;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProductDetailController {

    private final ProductDetailService productDetailService;

    @GetMapping("/api/v1/products/{productId}")
    public ProductDetailResponse detail(@PathVariable Long productId) {
        return ProductDetailResponse.from(productDetailService.findDetail(productId));
    }
}
