package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts.ProductView;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsReader;
import com.bbcc.kidly.boundedcontext.favorite.app.port.Favorites;
import com.bbcc.kidly.boundedcontext.favorite.app.port.Favorites.FavoriteRow;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteStats;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 찜 목록(최신순 커서 또는 productIds 필터)과 상품별 찜 수. 한 쪽은 찜 1번 + 상품 정보 1번(상품 수와 무관)으로 읽는다.
 * 찜한 뒤 판매 종료된 상품도 목록에 남고(표시 상태로 구분), 상품 정보가 없으면 그 칸은 null이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteQueryService {

    static final int MAX_PRODUCT_IDS = 50;

    private final Favorites favorites;
    private final FavoriteProducts favoriteProducts;
    private final FavoriteStatsReader favoriteStatsReader;

    /**
     * @param productIds 쉼표 구분 상품 ID(1~50개). 주면 그중 내가 찜한 것만 주고 커서 · size는 쓰지 않는다(cursor와 함께면 400)
     * @param cursor 직전 응답의 nextCursor
     * @param size 1~50, 기본 20
     */
    public FavoritePage findMine(Long memberId, String productIds, String cursor, String size) {
        if (productIds != null) {
            if (cursor != null) {
                throw new BusinessException(FavoriteErrorCode.INVALID_PRODUCT_IDS);
            }
            return new FavoritePage(withProducts(favorites.findMineIn(memberId, parseProductIds(productIds))), null);
        }
        int pageSize = FavoritePaging.pageSize(size);
        Limit limit = Limit.of(pageSize + 1);
        List<FavoriteRow> rows;
        if (cursor == null) {
            rows = favorites.findMine(memberId, limit);
        } else {
            FavoritePaging.Cursor after = FavoritePaging.Cursor.decode(cursor);
            rows = favorites.findMineAfter(memberId, after.at(), after.id(), limit);
        }
        List<FavoriteRow> page = rows.stream().limit(pageSize).toList();
        String next = rows.size() > pageSize
            ? new FavoritePaging.Cursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
            : null;
        return new FavoritePage(withProducts(page), next);
    }

    /**
     * 찜 수. 찜이 없으면 0. 상품 ID는 파싱한 값으로 돌려준다.
     */
    public FavoriteSummary summary(String productId) {
        Long id = FavoriteIds.productId(productId);
        long count = favoriteStatsReader.findById(id).map(FavoriteStats::getFavoriteCount).orElse(0L);
        return new FavoriteSummary(id, count);
    }

    static List<Long> parseProductIds(String productIds) {
        String[] parts = productIds.split(",", -1);
        // 중복을 합치기 전 개수로 센다(긴 입력을 끝까지 읽지 않는다)
        if (parts.length > MAX_PRODUCT_IDS) {
            throw new BusinessException(FavoriteErrorCode.INVALID_PRODUCT_IDS);
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : parts) {
            try {
                long value = Long.parseLong(part.strip());
                if (value <= 0) {
                    throw new BusinessException(FavoriteErrorCode.INVALID_PRODUCT_IDS);
                }
                ids.add(value);
            } catch (NumberFormatException e) {
                throw new BusinessException(FavoriteErrorCode.INVALID_PRODUCT_IDS);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException(FavoriteErrorCode.INVALID_PRODUCT_IDS);
        }
        return List.copyOf(ids);
    }

    private List<FavoriteItem> withProducts(List<FavoriteRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductView> products = favoriteProducts.find(rows.stream().map(FavoriteRow::getProductId).toList());
        return rows.stream().map(row -> FavoriteItem.of(row, products.get(row.getProductId()))).toList();
    }

    /**
     * @param nextCursor 다음 쪽 커서. 마지막 쪽 · productIds 필터면 null
     */
    public record FavoritePage(List<FavoriteItem> items, String nextCursor) {
    }

    /**
     * @param productName 상품 정보가 없으면 null
     * @param thumbnailUrl 상품 정보가 없거나 썸네일이 없으면 null
     * @param displayStatus 상품 정보가 없으면 null
     */
    public record FavoriteItem(Long productId, String productName, String thumbnailUrl, String displayStatus,
                               LocalDateTime favoritedAt) {

        static FavoriteItem of(FavoriteRow row, ProductView product) {
            return product == null
                ? new FavoriteItem(row.getProductId(), null, null, null, row.getCreatedAt())
                : new FavoriteItem(row.getProductId(), product.name(), product.thumbnailUrl(), product.displayStatus(),
                    row.getCreatedAt());
        }
    }

    public record FavoriteSummary(Long productId, long favoriteCount) {
    }
}
