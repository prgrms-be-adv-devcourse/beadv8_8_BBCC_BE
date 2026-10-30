package com.bbcc.kidly.boundedcontext.search.domain;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SearchErrorCode implements ErrorCode {

    SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "상품을 검색할 수 없습니다", "잠시 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
