package com.bbcc.kidly.boundedcontext.favorite.in.web.dto;

/**
 * 상품별 찜 수. 찜이 없으면 0.
 */
public record FavoriteSummaryResponse(String productId, long favoriteCount) {
}
