package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService.ReviewItem;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService.ReviewPage;
import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.support.QueryCounter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 리뷰 목록(상품별 · 내 리뷰): 최신순(created_at desc, id desc) 커서, 상품 정보는 상품 복사본에서 한 쿼리로.
 */
@IntegrationTest
class ReviewListQueryIntegrationTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long PRODUCT = 14L;
    private static final long OTHER_PRODUCT = 15L;
    // 나노초가 있는 고정 기준 시각(저장은 마이크로초)
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 1, 10, 0, 0, 123_456_123);

    @Autowired
    private ReviewQueryService queryService;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상품 목록은 그 상품 리뷰만 최신순이고, 같은 작성 시각이 쪽 경계에 걸려도 빠짐 · 겹침 없이 끝까지 읽는다")
    void productListNewestFirstAcrossTies() {
        replica(PRODUCT, "상품 A", DisplayStatus.ON_SALE);
        review(1, PRODUCT, ME, BASE.plusMinutes(1));
        review(2, PRODUCT, OTHER, BASE);
        review(3, PRODUCT, ME, BASE);
        review(4, PRODUCT, OTHER, BASE);
        review(5, PRODUCT, ME, BASE.minusMinutes(1));
        review(6, OTHER_PRODUCT, ME, BASE.plusMinutes(5));
        List<Long> expected = jdbcTemplate.queryForList("select order_item_id from community.review "
            + "where product_id = ? order by created_at desc, id desc", Long.class, PRODUCT);

        List<Long> read = readAll(cursor -> queryService.findByProduct(String.valueOf(PRODUCT), cursor, "2"));

        assertThat(expected).containsExactly(1L, 4L, 3L, 2L, 5L);
        assertThat(read).isEqualTo(expected);
    }

    @Test
    @DisplayName("상품 복사본이 없으면 상품 정보는 null이고, 판매 종료 상품의 리뷰도 표시 상태와 함께 보인다")
    void productInfoFromReplica() {
        replica(PRODUCT, "판매 끝난 상품", DisplayStatus.SALE_ENDED);
        review(11, PRODUCT, ME, BASE);
        review(12, 999L, ME, BASE.minusMinutes(1));

        assertThat(queryService.findByProduct(String.valueOf(PRODUCT), null, null).items())
            .extracting(ReviewItem::productName, ReviewItem::displayStatus)
            .containsExactly(tuple("판매 끝난 상품", "SALE_ENDED"));
        assertThat(queryService.findMine(ME, null, null).items())
            .extracting(ReviewItem::orderItemId, ReviewItem::productName, ReviewItem::thumbnailUrl,
                ReviewItem::displayStatus)
            .containsExactly(tuple(11L, "판매 끝난 상품", "https://img/14.png", "SALE_ENDED"),
                tuple(12L, null, null, null));
    }

    @Test
    @DisplayName("내 리뷰는 내 것만 최신순이고, 여러 상품이 섞여도 항목마다 그 상품 정보가 붙는다")
    void myReviewsAcrossProducts() {
        replica(PRODUCT, "상품 A", DisplayStatus.ON_SALE);
        replica(OTHER_PRODUCT, "상품 B", DisplayStatus.SOLD_OUT);
        review(21, PRODUCT, ME, BASE);
        review(22, OTHER_PRODUCT, ME, BASE.plusMinutes(1));
        review(23, PRODUCT, OTHER, BASE.plusMinutes(2));

        List<Long> read = readAll(cursor -> queryService.findMine(ME, cursor, "1"));

        assertThat(read).containsExactly(22L, 21L);
        assertThat(queryService.findMine(ME, null, null).items())
            .extracting(ReviewItem::productName, ReviewItem::displayStatus)
            .containsExactly(tuple("상품 B", "SOLD_OUT"), tuple("상품 A", "ON_SALE"));
    }

    @Test
    @DisplayName("한 쪽을 읽는 쿼리는 상품 정보를 포함해 1번이다(상품 목록 · 내 리뷰, 첫 쪽 · 다음 쪽)")
    void singleQueryPerPage() {
        for (int i = 0; i < 6; i++) {
            replica(100 + i, "상품 " + i, DisplayStatus.ON_SALE);
            review(30 + i, 100 + i, ME, BASE.minusMinutes(i));
            review(40 + i, PRODUCT, OTHER, BASE.minusMinutes(i));
        }
        replica(PRODUCT, "상품 A", DisplayStatus.ON_SALE);
        AtomicReference<ReviewPage> mine = new AtomicReference<>();
        AtomicReference<ReviewPage> product = new AtomicReference<>();

        long mineFirst = queryCounter.count(() -> mine.set(queryService.findMine(ME, null, "3")));
        long mineNext = queryCounter.count(() -> queryService.findMine(ME, mine.get().nextCursor(), "3"));
        long productFirst = queryCounter.count(
            () -> product.set(queryService.findByProduct(String.valueOf(PRODUCT), null, "3")));
        long productNext = queryCounter.count(
            () -> queryService.findByProduct(String.valueOf(PRODUCT), product.get().nextCursor(), "3"));

        assertThat(mine.get().items()).hasSize(3).allSatisfy(item -> assertThat(item.productName()).isNotNull());
        assertThat(List.of(mineFirst, mineNext, productFirst, productNext)).containsOnly(1L);
    }

    private List<Long> readAll(Function<String, ReviewPage> page) {
        List<Long> read = new ArrayList<>();
        String cursor = null;
        do {
            ReviewPage current = page.apply(cursor);
            current.items().forEach(item -> read.add(item.orderItemId()));
            cursor = current.nextCursor();
        } while (cursor != null);
        return read;
    }

    private void review(long orderItemId, long productId, long memberId, LocalDateTime createdAt) {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at) values (?, ?, ?, '닉', 4, '본문', ?, ?)",
            orderItemId, productId, memberId, createdAt, createdAt);
    }

    private void replica(long productId, String name, DisplayStatus status) {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
            productId, name, "https://img/" + productId + ".png", status.name(), BASE, BASE, BASE);
    }
}
