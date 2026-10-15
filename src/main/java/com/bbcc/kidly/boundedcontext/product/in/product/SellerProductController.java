package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping
    public ResponseEntity<ProductRegisterResponse> register(
        @LoginMember AuthMember member,
        @Valid @RequestBody ProductRegisterRequest request) {
        ProductRegisterResponse body = ProductRegisterResponse.from(
            productRegistrationService.register(member.id(), request));
        return ResponseEntity.created(URI.create(PRODUCTS_PATH + "/" + body.productId())).body(body);
    }
}
