package com.bbcc.kidly.boundedcontext.product.in.type;

import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryTreeResult;
import com.bbcc.kidly.boundedcontext.product.in.type.dto.CategoryInputsResponse;
import com.bbcc.kidly.boundedcontext.product.in.type.dto.CategoryTreeResponse;
import com.bbcc.kidly.global.security.PublicApi;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PublicApi
    @GetMapping("/api/v1/categories")
    public List<CategoryTreeResponse> list() {
        List<CategoryTreeResponse> responses = new ArrayList<>();
        for (CategoryTreeResult result : categoryService.findTree()) {
            responses.add(CategoryTreeResponse.from(result));
        }
        return responses;
    }

    @PublicApi
    @GetMapping("/api/v1/categories/{categoryId}/attributes")
    public CategoryInputsResponse inputs(@PathVariable Long categoryId) {
        return CategoryInputsResponse.from(categoryService.findInputs(categoryId));
    }
}
