package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductFilter;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductRegistrationService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductSaleService;
import com.bbcc.kidly.boundedcontext.product.app.product.ProductUpdateService;
import com.bbcc.kidly.boundedcontext.product.app.product.SellerProductService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductUpdateRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductRegisterResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductSaleStatusResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.SellerProductDetailResponse;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.SellerProductSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SellerProductController.PRODUCTS_PATH)
@RequiredArgsConstructor
public class SellerProductController {

    public static final String PRODUCTS_PATH = "/api/v1/seller/products";

    private final ProductRegistrationService productRegistrationService;
    private final ProductSaleService productSaleService;
    private final ProductUpdateService productUpdateService;
    private final SellerProductService sellerProductService;

    @GetMapping
    public PageResponse<SellerProductSummaryResponse> list(
        @LoginMember AuthMember member,
        @RequestParam(required = false) ProductStatus status,
        @RequestParam(defaultValue = "false") boolean lowStock,
        @PageableDefault Pageable pageable) {
        return PageResponse.from(sellerProductService
            .findMyProducts(member.id(), new SellerProductFilter(status, lowStock), pageable)
            .map(SellerProductSummaryResponse::from));
    }

    @GetMapping("/{productId}")
    public SellerProductDetailResponse detail(@LoginMember AuthMember member, @PathVariable Long productId) {
        return SellerProductDetailResponse.from(sellerProductService.findMyProductDetail(member.id(), productId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductRegisterResponse register(
        @LoginMember AuthMember member,
        @Valid @RequestBody ProductRegisterRequest request,
        HttpServletResponse response) {
        var body = ProductRegisterResponse.from(productRegistrationService.register(member.id(), request));
        response.setHeader(HttpHeaders.LOCATION, PRODUCTS_PATH + "/" + body.productId());
        return body;
    }

    @PutMapping("/{productId}")
    public SellerProductDetailResponse update(
        @LoginMember AuthMember member,
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request) {
        return SellerProductDetailResponse.from(productUpdateService.update(member.id(), productId, request));
    }

    @PutMapping("/{productId}/sale-status")
    public ProductSaleStatusResponse changeSaleStatus(
        @LoginMember AuthMember member,
        @PathVariable Long productId,
        @Valid @RequestBody ProductSaleStatusRequest request) {
        return ProductSaleStatusResponse.from(productSaleService.changeSaleStatus(
            member.id(), productId, ProductStatus.valueOf(request.status())));
    }

    @PutMapping("/{productId}/delisting")
    public ProductSaleStatusResponse delist(@LoginMember AuthMember member, @PathVariable Long productId) {
        return ProductSaleStatusResponse.from(productSaleService.delist(member.id(), productId));
    }
}
