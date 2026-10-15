package com.bbcc.kidly.boundedcontext.product.app.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 상품 목록의 쿼리 파라미터다. 보내지 않은 값은 기본값으로 채워 읽는다.
 *
 * @param attributes 고른 항목 값이다. "항목code:값ID" 형식의 문자열 목록이다 (예: COLOR:3)
 * @param sort 정렬 이름이다. 보내지 않으면 NEWEST다
 */
public record ProductListRequest(
    Long categoryId,
    @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
    String keyword,
    // 항목 code는 대문자와 _이고, 값 ID는 Long 범위(최대 19자리)를 넘지 않는 양의 정수다
    List<@Pattern(regexp = "[A-Z_]+:[1-9][0-9]{0,17}", message = "항목 값은 항목코드:값ID 형식이어야 합니다.") String>
        attributes,
    @Pattern(regexp = "NEWEST|PRICE_ASC|PRICE_DESC", message = "정렬은 NEWEST, PRICE_ASC, PRICE_DESC 중 하나여야 합니다.")
    String sort,
    @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
    Integer page,
    @Min(value = 1, message = "페이지 크기는 1 이상 100 이하여야 합니다.")
    @Max(value = 100, message = "페이지 크기는 1 이상 100 이하여야 합니다.")
    Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "NEWEST";

    public List<String> attributesOrEmpty() {
        if (attributes == null) {
            return List.of();
        }
        return attributes;
    }

    public String sortOrDefault() {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        return sort;
    }

    public int pageOrDefault() {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    public int sizeOrDefault() {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return size;
    }
}
