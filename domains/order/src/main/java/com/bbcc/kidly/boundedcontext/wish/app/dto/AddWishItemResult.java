package com.bbcc.kidly.boundedcontext.wish.app.dto;

/**
 * 조르기 담기 결과(#640).
 *
 * @param created 새 줄이면 true, 같은 옵션 줄에 수량을 더했으면 false
 */
public record AddWishItemResult(WishView.Line line, boolean created) {
}
