package com.bbcc.kidly.boundedcontext.product.in.type;

import com.bbcc.kidly.boundedcontext.product.app.type.ProductTypeService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeCreateRequest;
import com.bbcc.kidly.boundedcontext.product.in.type.dto.ProductTypeResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/product-types")
@RequiredArgsConstructor
public class AdminProductTypeController {

    private final ProductTypeService productTypeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductTypeResponse create(
        @Valid @RequestBody ProductTypeCreateRequest request, HttpServletResponse response) {
        var result = productTypeService.create(request);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/product-types/" + result.productTypeId());
        return ProductTypeResponse.from(result);
    }

    @PutMapping("/{productTypeId}")
    public ProductTypeResponse change(
        @PathVariable Long productTypeId, @Valid @RequestBody ProductTypeChangeRequest request) {
        return ProductTypeResponse.from(productTypeService.change(productTypeId, request));
    }

    @GetMapping
    public List<ProductTypeResponse> list() {
        return productTypeService.findAll().stream().map(ProductTypeResponse::from).toList();
    }

    @DeleteMapping("/{productTypeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long productTypeId) {
        productTypeService.delete(productTypeId);
    }
}
