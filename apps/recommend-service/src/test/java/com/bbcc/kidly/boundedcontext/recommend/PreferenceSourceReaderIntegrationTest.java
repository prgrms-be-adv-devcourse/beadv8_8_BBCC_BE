package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    private void childActivity(Long childProfileId, LocalDateTime occurredAt) {
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, 1L, childProfileId, occurredAt, 100L,
            null, null, null);
    }

    private void memberActivity(Long memberId, LocalDateTime occurredAt) {
        PreferenceTestData.insertActivity(jdbc, ActivityType.PRODUCT_VIEWED, memberId, null, occurredAt, 100L,
            null, null, null);
    }
}
