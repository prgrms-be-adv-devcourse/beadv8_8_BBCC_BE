package com.bbcc.kidly.boundedcontext.favorite.in.web.dto;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteQueryService.FavoriteItem;
import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteQueryService.FavoritePage;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 찜 목록. ID는 문자열, 시각은 LocalDateTime(한국 시간).
 *
 * @param nextCursor 다음 쪽 커서(불투명 문자열). 마지막 쪽 · productIds 필터면 null
 */
public record FavoriteListResponse(List<Item> items, String nextCursor) {

    public static FavoriteListResponse from(FavoritePage page) {
        return new FavoriteListResponse(page.items().stream().map(Item::from).toList(), page.nextCursor());
    }

    /**
     * @param productName 상품 정보가 없으면 null
     * @param thumbnailUrl 상품 정보가 없거나 썸네일이 없으면 null
     * @param displayStatus ON_SALE · SOLD_OUT · SALE_ENDED. 상품 정보가 없으면 null
     */
    public record Item(String productId, String productName, String thumbnailUrl, String displayStatus,
                       LocalDateTime favoritedAt) {

        static Item from(FavoriteItem item) {
            return new Item(String.valueOf(item.productId()), item.productName(), item.thumbnailUrl(),
                item.displayStatus(), item.favoritedAt());
        }
    }
}
