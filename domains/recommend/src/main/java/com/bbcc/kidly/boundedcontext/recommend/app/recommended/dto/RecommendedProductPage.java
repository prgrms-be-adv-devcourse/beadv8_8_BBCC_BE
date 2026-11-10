package com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

/**
 * 더보기 목록의 한 쪽과, 그 목록의 기준 시각이다.
 *
 * @param asOf 1쪽을 만든 시각이다. 요청이 정해서 주지 않으면 요청을 받은 시각(한국 시간, 초 단위)이다
 */
public record RecommendedProductPage(Page<RecommendedProductResult> page, LocalDateTime asOf) {
}
