package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.app.ProductListService;
import com.bbcc.kidly.boundedcontext.search.app.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.search.in.dto.ProductListItemResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 상품 목록. 상품의 목록 API와 경로 · 요청 · 응답이 같아, 이 컨트롤러는 apps/search-service에서만 띄운다.
 */
@RestController
@RequiredArgsConstructor
public class ProductListController {

    private final ProductListService productListService;

    @GetMapping("/api/v1/products")
    public PageResponse<ProductListItemResponse> list(@Valid @ModelAttribute ProductListRequest request) {
        return PageResponse.from(productListService.list(request).map(ProductListItemResponse::from));
    }
}
