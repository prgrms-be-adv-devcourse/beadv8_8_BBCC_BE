package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService.ReplicaHealth;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.ModerationOverview;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.RecentHidden;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 운영 조회용 읽기(복사본 상태 · 검사 상태별 현황)를 실제 DB로 확인한다. 테스트 설정의 재시도 상한은 3이다.
 */
@IntegrationTest
class CommunityOpsQueryIntegrationTest {

    // 나노초가 있는 고정 기준 시각(저장은 마이크로초)
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 6, 10, 0, 0, 123_456_789);
    private static final LocalDateTime STORED = BASE.withNano(123_456_000);

    @Autowired
    private ReplicaHealthService replicaHealthService;

    @Autowired
    private ReviewModerationAdminService moderationAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("복사본이 없으면 0개 · 표시 상태별 0 · 시각 null")
    void emptyReplicas() {
        assertThat(replicaHealthService.health()).isEqualTo(new ReplicaHealth(0,
            Map.of("ON_SALE", 0L, "SOLD_OUT", 0L, "SALE_ENDED", 0L), null, null));
    }

    @Test
    @DisplayName("복사본 수 · 표시 상태별 수 · 마지막 반영(수정) 시각 · 가장 늦은 원본 발생 시각")
    void replicaHealth() {
        replica(1, "ON_SALE", BASE, BASE.minusDays(1));
        replica(2, "ON_SALE", BASE.minusHours(1), BASE);
        replica(3, "SALE_ENDED", BASE.minusHours(2), BASE.minusDays(2));

        ReplicaHealth health = replicaHealthService.health();

        assertThat(health.total()).isEqualTo(3);
        assertThat(health.byDisplayStatus()).containsExactlyInAnyOrderEntriesOf(
            Map.of("ON_SALE", 2L, "SOLD_OUT", 0L, "SALE_ENDED", 1L));
        // 표시 상태 순서(ON_SALE · SOLD_OUT · SALE_ENDED)로 정해져 있다
        assertThat(health.byDisplayStatus().keySet()).containsExactly("ON_SALE", "SOLD_OUT", "SALE_ENDED");
        assertThat(health.lastUpdatedAt()).isEqualTo(STORED);
        assertThat(health.lastSourceOccurredAt()).isEqualTo(STORED);
    }

    @Test
    @DisplayName("검사 현황: 상태별 수(대기는 상한 전 · 상한 도달로 나눔, 없는 상태 0), 가장 오래된 대기 작성 시각, 최근 숨김(상한 적용)")
    void moderationOverview() {
        review(1, "PENDING", 0, null, null, BASE.minusHours(3));
        review(2, "PENDING", 2, null, null, BASE.minusHours(5));
        review(3, "PENDING", 3, null, null, BASE.minusHours(9));
        review(4, "HIDDEN", 0, "HATE", BASE.minusMinutes(30), BASE);
        review(5, "HIDDEN", 0, "SEXUAL", BASE.minusMinutes(10), BASE);
        review(6, "HIDDEN", 0, "OTHER", BASE.minusMinutes(20), BASE);
        review(7, "CONFIRMED_HIDDEN", 0, "HATE", BASE, BASE);
        review(8, "PASSED", 0, null, BASE, BASE);

        ModerationOverview overview = moderationAdminService.overview(2);

        assertThat(overview.counts()).containsExactlyInAnyOrderEntriesOf(Map.of("PENDING", 2L,
            "PENDING_EXHAUSTED", 1L, "PASSED", 1L, "HIDDEN", 3L, "CONFIRMED_HIDDEN", 1L, "RESTORED", 0L));
        assertThat(overview.oldestPendingCreatedAt()).isEqualTo(BASE.minusHours(5).withNano(123_456_000));
        assertThat(overview.recentHidden()).extracting(RecentHidden::reason).containsExactly("SEXUAL", "OTHER");
        assertThat(overview.recentHidden().getFirst().moderatedAt())
            .isEqualTo(BASE.minusMinutes(10).withNano(123_456_000));
    }

    @Test
    @DisplayName("리뷰가 없으면 모든 상태 0 · 가장 오래된 대기 null · 최근 숨김 없음")
    void emptyModeration() {
        ModerationOverview overview = moderationAdminService.overview(5);

        assertThat(overview.counts().values()).containsOnly(0L);
        assertThat(overview.counts()).hasSize(6);
        assertThat(overview.oldestPendingCreatedAt()).isNull();
        assertThat(overview.recentHidden()).isEmpty();
    }

    private void replica(long productId, String status, LocalDateTime updatedAt, LocalDateTime occurredAt) {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, ?, null, ?, ?, ?, ?)",
            productId, "상품 " + productId, status, micros(occurredAt), micros(updatedAt), micros(updatedAt));
    }

    private void review(long orderItemId, String status, int attempts, String reason, LocalDateTime moderatedAt,
        LocalDateTime createdAt) {
        jdbcTemplate.update("insert into community.review (order_item_id, product_id, member_id, nickname, rating, "
            + "content, created_at, updated_at, moderation_status, moderation_attempts, moderation_reason, "
            + "moderated_at) values (?, 14, ?, '닉', 4, '본문', ?, ?, ?, ?, ?, ?)",
            orderItemId, 100 + orderItemId, micros(createdAt), micros(createdAt), status, attempts, reason,
            micros(moderatedAt));
    }

    // 앱은 마이크로초로 잘라 저장한다(ReviewTimes). DB에 직접 넣는 값도 같게 맞춘다(DB는 반올림한다)
    private static LocalDateTime micros(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
