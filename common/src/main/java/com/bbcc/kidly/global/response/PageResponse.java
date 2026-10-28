package com.bbcc.kidly.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 목록 응답. 엔티티 Page는 Page.map으로 응답 DTO로 바꾼 뒤 넘긴다.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext());
    }
}
