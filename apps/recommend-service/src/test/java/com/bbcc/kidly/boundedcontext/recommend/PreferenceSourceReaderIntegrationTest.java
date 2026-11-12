package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 선호 계산 배치의 입력 읽기가 실제 PostgreSQL에서 활동 기록 · 구매 기록 · 상품 복사본을 읽는 동작을 확인한다.
 */
@IntegrationTest
class PreferenceSourceReaderIntegrationTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Autowired
    private PreferenceSourceReader reader;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("PREF-11 활동은 활동 기준, 구매확정은 구매 기준으로 기간 안인 자녀만 중복 없이 ID 순으로 읽는다")
    void readChildTargetsWithinEachPeriod() {
        LocalDateTime purchaseSince = SINCE.minusDays(150);
        childActivity(1L, SINCE);
        childActivity(2L, SINCE.minusSeconds(1));
        PreferenceTestData.insertPurchase(jdbc, 3L, 100L, SINCE.minusDays(100));
        PreferenceTestData.insertPurchase(jdbc, 5L, 100L, purchaseSince.minusSeconds(1));
        childActivity(4L, SINCE.plusDays(1));
        PreferenceTestData.insertPurchase(jdbc, 4L, 100L, SINCE.plusDays(1));
        memberActivity(9L, SINCE.plusDays(1));
        childActivity(6L, SINCE.plusDays(2));
        childActivity(7L, SINCE.minusDays(100));
        PreferenceTestData.insertPurchase(jdbc, 8L, 100L, purchaseSince);

        assertThat(reader.findChildTargets(SINCE, purchaseSince, 0, 10)).containsExactly(1L, 3L, 4L, 6L, 8L);
    }

    @Test
    @DisplayName("PREF-11 삭제한 자녀는 활동과 구매가 있어도 대상에서 빠지고, 복사본이 없는 자녀와 삭제하지 않은 자녀는 대상이다")
    void excludeDeletedChildrenFromTargets() {
        childActivity(1L, SINCE);
        PreferenceTestData.insertPurchase(jdbc, 1L, 100L, SINCE);
        PreferenceTestData.insertPurchase(jdbc, 2L, 100L, SINCE);
        childActivity(3L, SINCE);
        childActivity(4L, SINCE);
        insertReplicaChild(1L, SINCE.plusHours(1));
        insertReplicaChild(2L, SINCE.plusHours(1));
        insertReplicaChild(4L, null);

        assertThat(reader.findChildTargets(SINCE, SINCE, 0, 10)).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("PREF-11 삭제한 자녀가 앞에 있어도 limit개는 삭제하지 않은 자녀로 채워 페이지가 이어진다")
    void fillPageWithoutDeletedChildren() {
        childActivity(1L, SINCE);
        childActivity(2L, SINCE);
        childActivity(3L, SINCE);
        insertReplicaChild(1L, SINCE.plusHours(1));

        assertThat(reader.findChildTargets(SINCE, SINCE, 0, 2)).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("대상 자녀는 after보다 큰 ID만 limit개까지 읽는다")
    void readChildTargetsAfterIdWithLimit() {
        childActivity(1L, SINCE);
        childActivity(3L, SINCE);
        childActivity(4L, SINCE);
        childActivity(6L, SINCE);

        assertThat(reader.findChildTargets(SINCE, SINCE, 1, 2)).containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("대상 회원은 자녀를 고르지 않은 활동만 세고 after보다 큰 ID만 limit개까지 중복 없이 읽는다")
    void readMemberTargetsFromActivitiesWithoutChild() {
        memberActivity(1L, SINCE);
        memberActivity(1L, SINCE.plusDays(1));
        memberActivity(2L, SINCE.minusSeconds(1));
        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_ADDED, 3L, 7L, SINCE.plusDays(1), 100L, null,
            null, 1L);
        memberActivity(4L, SINCE);
        memberActivity(5L, SINCE);

        assertThat(reader.findMemberTargets(SINCE, 0, 10)).containsExactly(1L, 4L, 5L);
        assertThat(reader.findMemberTargets(SINCE, 1, 1)).containsExactly(4L);
    }

    @Test
    @DisplayName("PREF-01 자녀 활동은 기간 안의 검색 · 상세 진입 · 담기만 시각 순으로 읽고 빈 카테고리 · 브랜드는 복사본 값으로 채운다")
    void readChildActivitiesWithinPeriod() {
        LocalDateTime from = SINCE;
        LocalDateTime to = SINCE.plusHours(3);
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", null);
        PreferenceTestData.insertProduct(jdbc, 101L, 11L, "Other", null);
        insert(ActivityType.CART_ITEM_ADDED, 7L, to, 101L, 20L, "Own", 1L);
        insert(ActivityType.PRODUCT_VIEWED, 7L, from.plusHours(2), 100L, null, null, null);
        insert(ActivityType.SEARCH, 7L, from, null, 50L, null, null);
        insert(ActivityType.CART_ITEM_REMOVED, 7L, from.plusHours(1), 100L, null, null, 99L);
        insert(ActivityType.PRODUCT_VIEWED, 7L, from.minusSeconds(1), 102L, null, null, null);
        insert(ActivityType.PRODUCT_VIEWED, 7L, to.plusSeconds(1), 103L, null, null, null);
        insert(ActivityType.PRODUCT_VIEWED, 8L, from.plusHours(1), 100L, null, null, null);

        assertThat(reader.findChildActivities(7L, from, to)).containsExactly(
            new RecentActivity(ActivityType.SEARCH, null, 50L, null, from, false),
            new RecentActivity(ActivityType.PRODUCT_VIEWED, 100L, 10L, "Nike", from.plusHours(2), false),
            new RecentActivity(ActivityType.CART_ITEM_ADDED, 101L, 20L, "Own", to, false));
    }

    @Test
    @DisplayName("PREF-17 자녀 · 회원 활동은 검색 결과 클릭도 누른 상품의 복사본 카테고리 · 브랜드와 함께 읽는다")
    void readSearchResultClicksWithProductValues() {
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", null);
        insert(ActivityType.SEARCH_RESULT_CLICKED, 7L, SINCE, 100L, null, null, null);
        PreferenceTestData.insertActivity(jdbc, ActivityType.SEARCH_RESULT_CLICKED, 1L, null, SINCE.plusHours(1),
            100L, null, null, null);

        assertThat(reader.findChildActivities(7L, SINCE, SINCE.plusHours(3))).containsExactly(
            new RecentActivity(ActivityType.SEARCH_RESULT_CLICKED, 100L, 10L, "Nike", SINCE, false));
        assertThat(reader.findMemberActivities(1L, SINCE, SINCE.plusHours(3))).containsExactly(
            new RecentActivity(ActivityType.SEARCH_RESULT_CLICKED, 100L, 10L, "Nike", SINCE.plusHours(1), false));
    }

    @ParameterizedTest
    @CsvSource({"-60, true", "0, true", "1, false"})
    @DisplayName("PREF-07 담은 장바구니 줄은 기간 끝 시각까지 뺐을 때만 관심을 거둔 활동(withdrawn)이다")
    void markCartItemRemovedOnlyUntilEndOfPeriod(long removedSecondsAfterEnd, boolean removed) {
        LocalDateTime to = SINCE.plusHours(3);
        insert(ActivityType.CART_ITEM_ADDED, 7L, SINCE.plusHours(1), 100L, 10L, "Nike", 5L);
        insert(ActivityType.CART_ITEM_REMOVED, 7L, to.plusSeconds(removedSecondsAfterEnd), 100L, null, null, 5L);
        insert(ActivityType.CART_ITEM_ADDED, 7L, SINCE.plusHours(2), 101L, 10L, "Nike", 6L);

        assertThat(reader.findChildActivities(7L, SINCE, to)).extracting(RecentActivity::withdrawn)
            .containsExactly(removed, false);
    }

    @Test
    @DisplayName("PREF-18 자녀 활동은 조르기도 조른 상품의 복사본 카테고리 · 브랜드와 함께 읽는다")
    void readWishSentWithProductValues() {
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", null);
        insertWish(ActivityType.WISH_SENT, 301L, SINCE, 100L);

        assertThat(reader.findChildActivities(7L, SINCE, SINCE.plusHours(3))).containsExactly(
            new RecentActivity(ActivityType.WISH_SENT, 100L, 10L, "Nike", SINCE, false));
    }

    @ParameterizedTest
    @CsvSource({"-60, true", "0, true", "1, false"})
    @DisplayName("PREF-19 조르기는 기간 끝 시각까지 같은 조르기를 철회했을 때만 철회 표시가 붙고, 다른 조르기는 그대로다")
    void markWishWithdrawnOnlyUntilEndOfPeriod(long cancelledSecondsAfterEnd, boolean withdrawn) {
        LocalDateTime to = SINCE.plusHours(3);
        insertWish(ActivityType.WISH_SENT, 301L, SINCE.plusHours(1), 100L);
        insertWish(ActivityType.WISH_CANCELLED, 301L, to.plusSeconds(cancelledSecondsAfterEnd), null);
        insertWish(ActivityType.WISH_SENT, 302L, SINCE.plusHours(2), 101L);

        assertThat(reader.findChildActivities(7L, SINCE, to)).extracting(RecentActivity::withdrawn)
            .containsExactly(withdrawn, false);
    }

    @ParameterizedTest
    @CsvSource({"-60, true", "0, true", "1, false"})
    @DisplayName("PREF-21 회원의 찜은 상품 복사본 값과 함께 읽고, 기간 끝 시각까지 같은 찜을 해제했을 때만 관심을 거둔 활동이다")
    void readFavoritesMarkingRemovedOnlyUntilEndOfPeriod(long removedSecondsAfterEnd, boolean withdrawn) {
        LocalDateTime to = SINCE.plusHours(3);
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", null);
        PreferenceTestData.insertProduct(jdbc, 101L, 11L, "Other", null);
        insertFavorite(ActivityType.FAVORITE_ADDED, 401L, SINCE.plusHours(1), 100L);
        insertFavorite(ActivityType.FAVORITE_REMOVED, 401L, to.plusSeconds(removedSecondsAfterEnd), 100L);
        insertFavorite(ActivityType.FAVORITE_ADDED, 402L, SINCE.plusHours(2), 101L);

        assertThat(reader.findMemberActivities(1L, SINCE, to)).containsExactly(
            new RecentActivity(ActivityType.FAVORITE_ADDED, 100L, 10L, "Nike", SINCE.plusHours(1), withdrawn),
            new RecentActivity(ActivityType.FAVORITE_ADDED, 101L, 11L, "Other", SINCE.plusHours(2), false));
    }

    @Test
    @DisplayName("회원 활동은 자녀를 고르지 않은 활동만 읽는다")
    void readMemberActivitiesWithoutChild() {
        LocalDateTime to = SINCE.plusHours(3);
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, 1L, null, SINCE, 100L, 10L, "Nike", null);
        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_ADDED, 1L, 7L, SINCE, 100L, 10L, "Nike", 1L);
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, 2L, null, SINCE, 100L, 10L, "Nike", null);

        assertThat(reader.findMemberActivities(1L, SINCE, to)).containsExactly(
            new RecentActivity(ActivityType.PRODUCT_VIEWED, 100L, 10L, "Nike", SINCE, false));
    }

    @Test
    @DisplayName("PREF-16 자녀 구매는 기간 안의 구매확정을 상품 복사본의 브랜드와 함께 읽고, 복사본이 없으면 브랜드는 null이다")
    void readChildPurchasesWithBrand() {
        LocalDateTime to = SINCE.plusDays(1);
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", null);
        PreferenceTestData.insertPurchase(jdbc, 7L, 100L, to);
        PreferenceTestData.insertPurchase(jdbc, 7L, 100L, SINCE);
        PreferenceTestData.insertPurchase(jdbc, 7L, 999L, SINCE.plusHours(1));
        PreferenceTestData.insertPurchase(jdbc, 7L, 100L, SINCE.minusSeconds(1));
        PreferenceTestData.insertPurchase(jdbc, 7L, 100L, to.plusSeconds(1));
        PreferenceTestData.insertPurchase(jdbc, 8L, 100L, SINCE);

        assertThat(reader.findChildPurchases(7L, SINCE, to)).containsExactly(
            new ConfirmedPurchase(100L, "Nike", SINCE),
            new ConfirmedPurchase(999L, null, SINCE.plusHours(1)),
            new ConfirmedPurchase(100L, "Nike", to));
    }

    @Test
    @DisplayName("상품 벡터는 요청한 상품 중 벡터가 있는 상품만 값 그대로 읽는다")
    void readEmbeddingsOfRequestedProductsWithVector() {
        EmbeddingVector vector = PreferenceTestData.vectorOf(0.5f);
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", vector);
        PreferenceTestData.insertProduct(jdbc, 101L, 10L, "Nike", null);
        PreferenceTestData.insertProduct(jdbc, 102L, 10L, "Nike", PreferenceTestData.vectorOf(-0.5f));

        assertThat(reader.findEmbeddings(List.of(100L, 101L, 999L))).containsOnly(Map.entry(100L, vector));
    }

    @Test
    @DisplayName("상품 ID가 비어 있으면 빈 Map을 읽는다")
    void readNoEmbeddingsForEmptyProductIds() {
        assertThat(reader.findEmbeddings(List.of())).isEmpty();
    }

    private void insertReplicaChild(Long childProfileId, LocalDateTime deletedAt) {
        jdbc.update("""
            INSERT INTO recommend.replica_child (child_profile_id, member_id, birth_date, height_cm, weight_kg,
                gender, event_occurred_at, deleted_at, created_at, updated_at)
            VALUES (?, 1, DATE '2020-01-01', 100.0, 15.0, 'MALE', now(), ?, now(), now())
            """, childProfileId, deletedAt);
    }

    private void insert(ActivityType type, Long childProfileId, LocalDateTime occurredAt, Long productId,
        Long categoryId, String brand, Long cartItemId) {
        PreferenceTestData.insertActivity(jdbc, type, 1L, childProfileId, occurredAt, productId, categoryId, brand,
            cartItemId);
    }

    // 회원 1의 자녀 7이 남긴 조르기 · 조르기 철회 기록을 넣는다
    private void insertWish(ActivityType type, Long wishRequestId, LocalDateTime occurredAt, Long productId) {
        jdbc.update("""
            INSERT INTO recommend.activity_record (event_id, type, member_id, child_profile_id, occurred_at,
                product_id, wish_request_id, created_at, updated_at)
            VALUES (?, ?, 1, 7, ?, ?, ?, now(), now())
            """, UUID.randomUUID(), type.name(), occurredAt, productId, wishRequestId);
    }

    // 회원 1이 자녀를 고르지 않고 남긴 찜 · 찜 해제 기록을 넣는다
    private void insertFavorite(ActivityType type, Long favoriteId, LocalDateTime occurredAt, Long productId) {
        jdbc.update("""
            INSERT INTO recommend.activity_record (event_id, type, member_id, occurred_at, product_id, favorite_id,
                created_at, updated_at)
            VALUES (?, ?, 1, ?, ?, ?, now(), now())
            """, UUID.randomUUID(), type.name(), occurredAt, productId, favoriteId);
    }

    private void childActivity(Long childProfileId, LocalDateTime occurredAt) {
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, 1L, childProfileId, occurredAt, 100L,
            null, null, null);
    }

    private void memberActivity(Long memberId, LocalDateTime occurredAt) {
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, memberId, null, occurredAt, 100L,
            null, null, null);
    }
}
