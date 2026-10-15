package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductSaleService;
import com.bbcc.kidly.boundedcontext.product.app.product.SellerProductService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductSaleStatusResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.SellerProductDetailResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.SellerProductSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final SellerProductService sellerProductService;

    // 정렬은 등록 최신순으로 고정이라 pageable의 sort는 무시된다
    @GetMapping
    public PageResponse<SellerProductSummaryResponse> list(
        @LoginMember AuthMember member, @PageableDefault Pageable pageable) {
        return PageResponse.from(
            sellerProductService.findMyProducts(member.id(), pageable).map(SellerProductSummaryResponse::from));
    }

    // 판매자 본인의 상품 하나의 상세를 돌려준다
    @GetMapping("/{productId}")
    public SellerProductDetailResponse detail(@LoginMember AuthMember member, @PathVariable Long productId) {
        return SellerProductDetailResponse.from(sellerProductService.findMyProductDetail(member.id(), productId));
    }

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
