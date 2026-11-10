package com.bbcc.kidly.boundedcontext.recommend.in.companion.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductResult;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductsResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 함께 사면 좋은 상품 목록이다. source는 AI가 골랐는지(AI) 규칙으로 정했는지(RULE)를 나타내고, 후보가 없으면 products는 빈 목록이다.
 */
public record CompanionProductsResponse(String source, List<CompanionProductResponse> products) {

    public static CompanionProductsResponse from(CompanionProductsResult result) {
        List<CompanionProductResponse> products = new ArrayList<>();
        for (CompanionProductResult product : result.products()) {
            products.add(CompanionProductResponse.from(product));
        }
        // in은 domain 타입을 참조하지 않으므로 출처를 문자열(AI 또는 RULE)로 바꿔 담는다.
        return new CompanionProductsResponse(String.valueOf(result.source()), products);
    }
}
