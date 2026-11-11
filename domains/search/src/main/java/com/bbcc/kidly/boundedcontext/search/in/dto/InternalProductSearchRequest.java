package com.bbcc.kidly.boundedcontext.search.in.dto;

import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 서비스 사이 상품 검색의 쿼리 파라미터(#1191). 검색어 규칙은 목록 API와 같다. limit을 보내지 않으면 5개다.
 */
public record InternalProductSearchRequest(
    @NotBlank(message = "검색어를 보내 주세요.")
    @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
    String keyword,
    @PositiveOrZero(message = "최저 가격은 0 이상이어야 합니다.")
    Long minPrice,
    @PositiveOrZero(message = "최고 가격은 0 이상이어야 합니다.")
    Long maxPrice,
    @Min(value = 1, message = "가져올 수는 1 이상 10 이하여야 합니다.")
    @Max(value = 10, message = "가져올 수는 1 이상 10 이하여야 합니다.")
    Integer limit) {

    private static final int DEFAULT_LIMIT = 5;

    @AssertTrue(message = "최저 가격은 최고 가격보다 클 수 없습니다.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    public ProductSearchQuery toQuery() {
        return new ProductSearchQuery(keyword, minPrice, maxPrice, limit == null ? DEFAULT_LIMIT : limit);
    }
}
