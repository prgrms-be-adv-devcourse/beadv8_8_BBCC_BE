package com.bbcc.kidly.boundedcontext.product.in.type;

import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryChangeRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryCreateRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryResult;
import com.bbcc.kidly.boundedcontext.product.in.type.dto.CategoryResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 카테고리 API. 카테고리를 만들고 조회하고 바꾼다.
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(
        @Valid @RequestBody CategoryCreateRequest request, HttpServletResponse response) {
        CategoryResult result = categoryService.create(request);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/categories/" + result.categoryId());
        return CategoryResponse.from(result);
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse change(
        @PathVariable Long categoryId, @Valid @RequestBody CategoryChangeRequest request) {
        return CategoryResponse.from(categoryService.change(categoryId, request));
    }

    @GetMapping
    public List<CategoryResponse> list() {
        List<CategoryResponse> responses = new ArrayList<>();
        for (CategoryResult result : categoryService.findAll()) {
            responses.add(CategoryResponse.from(result));
        }
        return responses;
    }
}
