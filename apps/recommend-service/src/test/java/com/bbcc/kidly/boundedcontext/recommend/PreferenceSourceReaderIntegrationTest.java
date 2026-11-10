package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
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
    @DisplayName("기간 안에 활동했거나 구매확정한 자녀만 중복 없이 ID 순으로 읽는다")
    void readChildTargetsWithinPeriod() {
        childActivity(1L, SINCE);
        childActivity(2L, SINCE.minusSeconds(1));
        PreferenceTestData.insertPurchase(jdbc, 3L, 100L, SINCE);
        PreferenceTestData.insertPurchase(jdbc, 2L, 100L, SINCE.minusSeconds(1));
        childActivity(4L, SINCE.plusDays(1));
        PreferenceTestData.insertPurchase(jdbc, 4L, 100L, SINCE.plusDays(1));
        memberActivity(9L, SINCE.plusDays(1));
        childActivity(6L, SINCE.plusDays(2));

        assertThat(reader.findChildTargets(SINCE, SINCE, 0, 10)).containsExactly(1L, 3L, 4L, 6L);
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
    @DisplayName("자녀 활동은 기간 안의 검색 · 상세 진입 · 담기만 발생 시각 순으로 읽고, 카테고리 · 브랜드는 활동 값을 먼저 쓰고 없으면 상품 복사본 값을 쓴다")
    void readChildActivitiesWithinPeriod() {
        LocalDateTime from = SINCE;
        LocalDateTime to = SINCE.plusHours(3);
        PreferenceTestData.insertProduct(jdbc, 100L, 10L, "Nike", null);
        PreferenceTestData.insertProduct(jdbc, 101L, 11L, "Other", null);
        insert(ActivityType.CART_ITEM_ADDED, 7L, to, 101L, 20L, "Own", 1L);
        insert(ActivityType.PRODUCT_VIEWED, 7L, from.plusHours(2), 100L, null, null, null);
        insert(ActivityType.SEARCH, 7L, from, null, 50L, null, null);
        insert(ActivityType.CART_ITEM_REMOVED, 7L, from.plusHours(1), 100L, null, null, 99L);
        insert(ActivityType.PRODUCT_VIEWED, 7L, from.minusSeconds(1), 100L, null, null, null);
        insert(ActivityType.PRODUCT_VIEWED, 7L, to.plusSeconds(1), 100L, null, null, null);
        insert(ActivityType.PRODUCT_VIEWED, 8L, from.plusHours(1), 100L, null, null, null);

        assertThat(reader.findChildActivities(7L, from, to)).containsExactly(
            new RecentActivity(ActivityType.SEARCH, null, 50L, null, from, false),
            new RecentActivity(ActivityType.PRODUCT_VIEWED, 100L, 10L, "Nike", from.plusHours(2), false),
            new RecentActivity(ActivityType.CART_ITEM_ADDED, 101L, 20L, "Own", to, false));
    }

    @ParameterizedTest
    @CsvSource({"-60, true", "0, true", "1, false"})
    @DisplayName("PREF-07 담은 장바구니 줄은 기간 끝 시각까지 뺐을 때만 removedFromCart다")
    void markCartItemRemovedOnlyUntilEndOfPeriod(long removedSecondsAfterEnd, boolean removed) {
        LocalDateTime to = SINCE.plusHours(3);
        insert(ActivityType.CART_ITEM_ADDED, 7L, SINCE.plusHours(1), 100L, 10L, "Nike", 5L);
        insert(ActivityType.CART_ITEM_REMOVED, 7L, to.plusSeconds(removedSecondsAfterEnd), 100L, null, null, 5L);
        insert(ActivityType.CART_ITEM_ADDED, 7L, SINCE.plusHours(2), 101L, 10L, "Nike", 6L);

        assertThat(reader.findChildActivities(7L, SINCE, to)).extracting(RecentActivity::removedFromCart)
            .containsExactly(removed, false);
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
    @DisplayName("자녀 구매는 기간 안의 구매확정을 상품 복사본의 브랜드와 함께 읽고, 복사본이 없으면 브랜드는 null이다")
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

    private void insert(ActivityType type, Long childProfileId, LocalDateTime occurredAt, Long productId,
        Long categoryId, String brand, Long cartItemId) {
        PreferenceTestData.insertActivity(jdbc, type, 1L, childProfileId, occurredAt, productId, categoryId, brand,
            cartItemId);
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
