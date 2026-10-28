package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductSaleService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductSaleStatusResponse;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 상품 API. 관리자는 등록한 판매자와 상관없이 모든 상품을 다룬다.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductSaleService productSaleService;

    @PutMapping("/{productId}/sale-status")
    public ProductSaleStatusResponse changeSaleStatus(
        @PathVariable Long productId,
        @Valid @RequestBody ProductSaleStatusRequest request) {
        return ProductSaleStatusResponse.from(
            productSaleService.changeSaleStatusByAdmin(productId, ProductStatus.valueOf(request.status())));
    }
}
