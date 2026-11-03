package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteQueryService.FavoriteItem;
import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteQueryService.FavoritePage;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.support.QueryCounter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 내 찜 목록: 내 것만 최신순(created_at desc, id desc) 커서, productIds 필터, 판매 종료 상품도 남고 상품 정보가 없으면 null.
 * 쿼리는 찜 1번 + 상품 정보 1번(상품 수와 무관).
 */
@IntegrationTest
class FavoriteQueryIntegrationTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    // 나노초가 있는 고정 기준 시각(저장은 마이크로초)
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 1, 10, 0, 0, 123_456_123);

    @Autowired
    private FavoriteQueryService queryService;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("내 찜만 최신순이고, 같은 찜 시각이 쪽 경계에 걸려도 빠짐 · 겹침 없이 끝까지 읽는다")
    void mineNewestFirstAcrossTies() {
        favorite(ME, 1, BASE.plusMinutes(1));
        favorite(ME, 2, BASE);
        favorite(ME, 3, BASE);
        favorite(ME, 4, BASE);
        favorite(ME, 5, BASE.minusMinutes(1));
        favorite(OTHER, 6, BASE.plusMinutes(5));
        List<Long> expected = jdbcTemplate.queryForList("select product_id from community.favorite "
            + "where member_id = ? order by created_at desc, id desc", Long.class, ME);

        List<Long> read = new ArrayList<>();
        String cursor = null;
        do {
            FavoritePage page = queryService.findMine(ME, null, cursor, "2");
            page.items().forEach(item -> read.add(item.productId()));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(expected).containsExactly(1L, 4L, 3L, 2L, 5L);
        assertThat(read).isEqualTo(expected);
    }

    @Test
    @DisplayName("찜 뒤 판매 종료된 상품도 표시 상태와 함께 남고, 상품 정보가 없으면 null이다")
    void saleEndedStaysAndMissingIsNull() {
        replica(10, "판매 끝난 상품", "SALE_ENDED");
        favorite(ME, 10, BASE);
        favorite(ME, 11, BASE.minusMinutes(1));

        assertThat(queryService.findMine(ME, null, null, null).items())
            .extracting(FavoriteItem::productId, FavoriteItem::productName, FavoriteItem::displayStatus)
            .containsExactly(tuple(10L, "판매 끝난 상품", "SALE_ENDED"), tuple(11L, null, null));
    }

    @Test
    @DisplayName("productIds를 주면 그중 내가 찜한 것만, nextCursor는 null")
    void productIdsFilter() {
        favorite(ME, 21, BASE);
        favorite(ME, 22, BASE.minusMinutes(1));
        favorite(OTHER, 23, BASE);

        FavoritePage page = queryService.findMine(ME, "21,23,24", null, null);

        assertThat(page.items()).extracting(FavoriteItem::productId).containsExactly(21L);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("한 쪽을 읽는 쿼리는 찜 1번 + 상품 정보 1번이다(상품 수와 무관, 커서 · productIds 모두)")
    void twoQueriesPerPage() {
        for (int i = 0; i < 6; i++) {
            replica(100 + i, "상품 " + i, "ON_SALE");
            favorite(ME, 100 + i, BASE.minusMinutes(i));
        }
        AtomicReference<FavoritePage> first = new AtomicReference<>();

        long firstPage = queryCounter.count(() -> first.set(queryService.findMine(ME, null, null, "3")));
        long nextPage = queryCounter.count(() -> queryService.findMine(ME, null, first.get().nextCursor(), "3"));
        long filtered = queryCounter.count(() -> queryService.findMine(ME, "100,101,102,103", null, null));

        assertThat(first.get().items()).hasSize(3).allSatisfy(item -> assertThat(item.productName()).isNotNull());
        assertThat(List.of(firstPage, nextPage, filtered)).containsOnly(2L);
    }

    private void favorite(long memberId, long productId, LocalDateTime createdAt) {
        jdbcTemplate.update("insert into community.favorite (member_id, product_id, created_at, updated_at) "
            + "values (?, ?, ?, ?)", memberId, productId, createdAt, createdAt);
    }

    private void replica(long productId, String name, String status) {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
            productId, name, "https://img/" + productId + ".png", status, BASE, BASE, BASE);
    }
}
