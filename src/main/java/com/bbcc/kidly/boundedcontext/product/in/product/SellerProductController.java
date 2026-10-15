package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductSaleService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductSaleStatusResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 상품 API. 로그인한 판매자 본인의 상품만 다룬다.
 */
@RestController
@RequestMapping(SellerProductController.PRODUCTS_PATH)
@RequiredArgsConstructor
public class SellerProductController {

    public static final String PRODUCTS_PATH = "/api/v1/seller/products";

    private final ProductRegistrationService productRegistrationService;
    private final ProductSaleService productSaleService;

    @PostMapping
    public ResponseEntity<ProductRegisterResponse> register(
        @LoginMember AuthMember member,
        @Valid @RequestBody ProductRegisterRequest request) {
        ProductRegisterResponse body = ProductRegisterResponse.from(
            productRegistrationService.register(member.id(), request));
        return ResponseEntity.created(URI.create(PRODUCTS_PATH + "/" + body.productId())).body(body);
    }

    @PutMapping("/{productId}/sale-status")
    public ProductSaleStatusResponse changeSaleStatus(
        @LoginMember AuthMember member,
        @PathVariable Long productId,
        @Valid @RequestBody ProductSaleStatusRequest request) {
        return ProductSaleStatusResponse.from(productSaleService.startSale(member.id(), productId));
    }
}
