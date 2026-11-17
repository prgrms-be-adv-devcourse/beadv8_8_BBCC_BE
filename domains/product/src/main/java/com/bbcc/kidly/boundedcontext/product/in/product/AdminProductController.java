package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductChangedRepublishService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductSaleService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductChangedRepublishResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductSaleStatusResponse;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductSaleService productSaleService;
    private final ProductChangedRepublishService productChangedRepublishService;

    @PutMapping("/{productId}/sale-status")
    public ProductSaleStatusResponse changeSaleStatus(
        @PathVariable Long productId,
        @Valid @RequestBody ProductSaleStatusRequest request) {
        return ProductSaleStatusResponse.from(
            productSaleService.changeSaleStatusByAdmin(productId, ProductStatus.valueOf(request.status())));
    }

    @DeleteMapping("/{productId}/delisting")
    public ProductSaleStatusResponse revertDelisting(@PathVariable Long productId) {
        return ProductSaleStatusResponse.from(productSaleService.revertDelistByAdmin(productId));
    }

    @PostMapping("/changed-events")
    public ProductChangedRepublishResponse republishChangedEvents() {
        return new ProductChangedRepublishResponse(productChangedRepublishService.republishStartedSaleProducts());
    }
}
