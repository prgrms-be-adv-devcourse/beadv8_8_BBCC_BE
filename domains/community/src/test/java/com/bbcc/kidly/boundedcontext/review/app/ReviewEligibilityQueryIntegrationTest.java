package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReviewEligibilityQueryService.EligibleItem;
import com.bbcc.kidly.boundedcontext.review.app.ReviewEligibilityQueryService.EligiblePage;
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

@IntegrationTest
class ReviewEligibilityQueryIntegrationTest {

    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final LocalDateTime BASE = LocalDateTime.now().minusDays(1);

    @Autowired
    private ReviewEligibilityQueryService queryService;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("내 자격만 구매확정 최신순으로 보이고, 같은 시각이면 나중에 저장한 것이 먼저다")
    void onlyMineNewestFirst() {
        insert(101, ME, 1, BASE.minusHours(2));
        insert(102, ME, 1, BASE);
        insert(103, OTHER, 1, BASE.plusHours(1));
        insert(104, ME, 1, BASE);

        EligiblePage page = queryService.findEligible(ME, null, null);

        assertThat(page.items()).extracting(EligibleItem::orderItemId).containsExactly(104L, 102L, 101L);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서로 이어 읽으면 빠짐 · 겹침 없이 끝까지 읽고, 마지막 쪽의 nextCursor는 null이다")
    void cursorContinues() {
        for (int i = 0; i < 5; i++) {
            insert(200 + i, ME, 1, BASE.minusMinutes(i));
        }

        List<Long> read = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            EligiblePage page = queryService.findEligible(ME, cursor, "2");
            page.items().forEach(item -> read.add(item.orderItemId()));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(read).containsExactly(200L, 201L, 202L, 203L, 204L);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 구매확정 시각이 쪽 경계에 걸려도 빠짐 · 겹침 없이 (구매확정 시각 desc, id desc) 순서로 끝까지 읽는다")
    void sameConfirmedAtAcrossPages() {
        insert(501, ME, 1, BASE.plusMinutes(1));
        insert(502, ME, 1, BASE);
        insert(503, ME, 1, BASE);
        insert(504, ME, 1, BASE);
        insert(505, ME, 1, BASE.minusMinutes(1));
        List<Long> expected = jdbcTemplate.queryForList("select order_item_id from community.review_eligibility "
            + "where member_id = ? order by confirmed_at desc, id desc", Long.class, ME);

        List<Long> read = new ArrayList<>();
        String cursor = null;
        do {
            EligiblePage page = queryService.findEligible(ME, cursor, "2");
            page.items().forEach(item -> read.add(item.orderItemId()));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(expected).containsExactly(501L, 504L, 503L, 502L, 505L);
        assertThat(read).isEqualTo(expected);
    }

    @Test
    @DisplayName("상품 복사본이 있으면 이름 · 썸네일을 함께, 없으면 null로 주고 자격은 보인다")
    void productInfoFromReplica() {
        insertReplica(1, "상품 A", "https://img/1.png");
        insert(301, ME, 1, BASE);
        insert(302, ME, 999, BASE.minusMinutes(1));

        List<EligibleItem> items = queryService.findEligible(ME, null, null).items();

        assertThat(items).extracting(EligibleItem::orderItemId, EligibleItem::productName, EligibleItem::thumbnailUrl)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(301L, "상품 A", "https://img/1.png"),
                org.assertj.core.groups.Tuple.tuple(302L, null, null));
    }

    @Test
    @DisplayName("한 쪽을 읽는 쿼리는 상품 정보를 포함해 1번이다(자격 수와 상관없이, N+1 없음)")
    void singleQueryPerPage() {
        for (int i = 0; i < 5; i++) {
            insertReplica(10 + i, "상품 " + i, null);
            insert(400 + i, ME, 10 + i, BASE.minusMinutes(i));
        }
        AtomicReference<EligiblePage> first = new AtomicReference<>();

        long firstPage = queryCounter.count(() -> first.set(queryService.findEligible(ME, null, "3")));
        long nextPage = queryCounter.count(() -> queryService.findEligible(ME, first.get().nextCursor(), "3"));

        assertThat(first.get().items()).hasSize(3).allSatisfy(item -> assertThat(item.productName()).isNotNull());
        assertThat(firstPage).isEqualTo(1);
        assertThat(nextPage).isEqualTo(1);
    }

    private void insert(long orderItemId, long memberId, long productId, LocalDateTime confirmedAt) {
        jdbcTemplate.update("insert into community.review_eligibility (order_item_id, order_id, member_id, "
            + "product_id, option_id, size, confirmed_at, created_at, updated_at) "
            + "values (?, 1, ?, ?, 1, null, ?, now(), now())", orderItemId, memberId, productId, confirmedAt);
    }

    private void insertReplica(long productId, String name, String thumbnailUrl) {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, ?, ?, 'ON_SALE', now(), now(), now())",
            productId, name, thumbnailUrl);
    }
}
