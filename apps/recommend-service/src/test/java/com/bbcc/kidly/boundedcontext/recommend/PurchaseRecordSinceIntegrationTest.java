package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀가 기준 시각 이후에 구매확정한 상품 ID를 실제 PostgreSQL에서 읽는 동작을 확인한다.
 * 홈 맛보기 근거(산 적 있는 브랜드 · 전에 산 카테고리)가 최근 3개월 구매를 읽을 때 쓴다.
 */
@IntegrationTest
class PurchaseRecordSinceIntegrationTest {

    private static final Long CHILD_ID = 11L;
    private static final Long OTHER_CHILD_ID = 12L;
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 8, 11, 0, 0);

    @Autowired
    private PurchaseRecordRepository purchaseRecordRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("HOME-05 기준 시각 이후(같은 시각 포함)에 그 자녀가 구매확정한 상품 ID만 한 번씩 읽는다")
    void readProductIdsConfirmedSince() {
        PreferenceTestData.insertPurchase(jdbc, CHILD_ID, 101L, SINCE);
        PreferenceTestData.insertPurchase(jdbc, CHILD_ID, 102L, SINCE.plusDays(10));
        PreferenceTestData.insertPurchase(jdbc, CHILD_ID, 102L, SINCE.plusDays(20));
        PreferenceTestData.insertPurchase(jdbc, CHILD_ID, 103L, SINCE.minusSeconds(1));
        PreferenceTestData.insertPurchase(jdbc, OTHER_CHILD_ID, 104L, SINCE.plusDays(1));

        List<Long> productIds = transactionTemplate.execute(
            status -> purchaseRecordRepository.findProductIdsConfirmedSince(CHILD_ID, SINCE));

        assertThat(productIds).containsExactlyInAnyOrder(101L, 102L);
    }
}
