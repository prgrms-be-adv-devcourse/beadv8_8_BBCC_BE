package com.bbcc.kidly.boundedcontext.product.app.search.dto;

import java.util.List;

/**
 * 상품 목록의 쿼리 파라미터다. 보내지 않은 값은 기본값으로 채워 읽는다.
 *
 * @param attributes 고른 항목 값이다. "항목code:값ID" 형식의 문자열 목록이다 (예: COLOR:3)
 * @param sort 정렬 이름이다. 보내지 않으면 NEWEST다
 */
public record ProductListRequest(
    Long categoryId, String keyword, List<String> attributes, String sort, Integer page, Integer size) {

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
