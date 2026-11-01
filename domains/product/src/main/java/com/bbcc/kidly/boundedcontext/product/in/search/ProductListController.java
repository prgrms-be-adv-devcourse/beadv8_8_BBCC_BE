package com.bbcc.kidly.boundedcontext.product.in.search;

import com.bbcc.kidly.boundedcontext.product.app.search.ProductListService;
import com.bbcc.kidly.boundedcontext.product.app.search.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.product.in.search.dto.ProductListItemResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProductListController {

    private final ProductListService productListService;

    @GetMapping("/api/v1/products")
    public PageResponse<ProductListItemResponse> list(@Valid @ModelAttribute ProductListRequest request) {
        return PageResponse.from(productListService.list(request).map(ProductListItemResponse::from));
    }
}
