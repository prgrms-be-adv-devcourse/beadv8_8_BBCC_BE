package com.bbcc.kidly.boundedcontext.review.in.mcp;

import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService.ProductSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * review_summary: 상품 하나의 리뷰 수 · 평균 별점 · 핏 분포(공개 리뷰 기준, 상품 요약 집계).
 */
class ReviewSummaryTool implements CommunityMcpTool {

    private final ReviewQueryService reviewQueries;

    ReviewSummaryTool(ReviewQueryService reviewQueries) {
        this.reviewQueries = reviewQueries;
    }

    @Override
    public String name() {
        return "review_summary";
    }

    @Override
    public String description() {
        return "상품 하나의 리뷰 수 · 평균 별점 · 사이즈 핏 분포(공개 리뷰 기준). 리뷰가 없으면 0 · null.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("productId", Map.of("type", List.of("string", "integer"),
                "description", "상품 ID(양의 정수)")),
            "required", List.of("productId"));
    }

    @Override
    public Map<String, Object> call(McpToolArguments arguments) {
        ProductSummary summary = reviewQueries.summary(arguments.requiredIdText("productId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", String.valueOf(summary.productId()));
        result.put("reviewCount", summary.reviewCount());
        result.put("averageRating", summary.averageRating());
        result.put("fitSummary", Map.of("small", summary.fitSummary().small(),
            "trueToSize", summary.fitSummary().trueToSize(), "large", summary.fitSummary().large()));
        return result;
    }
}
