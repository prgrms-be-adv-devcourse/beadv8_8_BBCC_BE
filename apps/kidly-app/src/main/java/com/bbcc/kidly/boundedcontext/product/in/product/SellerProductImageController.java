package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductImageUploadService;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductImageUploadUrlRequest;
import com.bbcc.kidly.boundedcontext.product.in.product.dto.ProductImageUploadUrlsResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 상품 이미지 API. 판매자가 이미지를 S3에 직접 올릴 업로드 주소를 발급한다.
 */
@RestController
@RequestMapping("/api/v1/seller/product-images")
@RequiredArgsConstructor
public class SellerProductImageController {

    private final ProductImageUploadService productImageUploadService;

    @PostMapping("/upload-urls")
    public ProductImageUploadUrlsResponse issueUploadUrls(
        @LoginMember AuthMember member,
        @Valid @RequestBody ProductImageUploadUrlRequest request) {
        return ProductImageUploadUrlsResponse.from(productImageUploadService.issue(member.id(), request));
    }
}
