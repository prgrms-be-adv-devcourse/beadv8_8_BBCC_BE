package com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto;

import com.bbcc.kidly.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 더보기 목록 응답이다. 공통 목록 응답의 칸에 목록의 기준 시각 asOf를 더했다.
 */
public record RecommendedProductPageResponse(
    List<RecommendedProductResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    LocalDateTime asOf
) {

    public static RecommendedProductPageResponse of(PageResponse<RecommendedProductResponse> page,
        LocalDateTime asOf) {
        return new RecommendedProductPageResponse(page.content(), page.page(), page.size(),
            page.totalElements(), page.totalPages(), page.hasNext(), asOf);
    }
}
