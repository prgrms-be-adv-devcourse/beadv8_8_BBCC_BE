package com.bbcc.kidly.boundedcontext.search.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 검색 결과 클릭 기록 요청이다. 검색어 · 정렬은 그 목록을 찾을 때 상품 목록 API에 보낸 값을 그대로 보낸다.
 *
 * @param rank 보여 준 목록에서 누른 상품의 위치. 첫 페이지 첫 상품이 1이다
 * @param sort 그 목록의 정렬. 보내지 않으면 상품 목록 API의 기본값(NEWEST)이다
 */
public record SearchClickRequest(
    @NotBlank(message = "검색어를 입력해 주세요.")
    @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
    String keyword,
    @NotNull(message = "상품 ID를 입력해 주세요.")
    @Positive(message = "상품 ID는 양수여야 합니다.")
    Long productId,
    // 상품 목록은 1만 건 너머를 보여 주지 않으므로(Elasticsearch max_result_window) 그보다 뒤 위치는 없다
    @NotNull(message = "순위를 입력해 주세요.")
    @Min(value = 1, message = "순위는 1 이상 10000 이하여야 합니다.")
    @Max(value = 10_000, message = "순위는 1 이상 10000 이하여야 합니다.")
    Integer rank,
    @Pattern(regexp = "NEWEST|PRICE_ASC|PRICE_DESC|RELEVANCE",
        message = "정렬은 NEWEST, PRICE_ASC, PRICE_DESC, RELEVANCE 중 하나여야 합니다.")
    String sort) {

    private static final String DEFAULT_SORT = "NEWEST";

    public String sortOrDefault() {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        return sort;
    }
}
