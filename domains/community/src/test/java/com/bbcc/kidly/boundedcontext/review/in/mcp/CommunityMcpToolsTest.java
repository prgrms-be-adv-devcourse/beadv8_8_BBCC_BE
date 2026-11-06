package com.bbcc.kidly.boundedcontext.review.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService;
import com.bbcc.kidly.boundedcontext.review.app.ReplicaHealthService.ReplicaHealth;
import com.bbcc.kidly.boundedcontext.review.app.ReviewErrorCode;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.ModerationOverview;
import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationAdminService.RecentHidden;
import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Summary;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 도구 정의(SDK 없이): 이름 · 입력 스키마 · 인자 해석 · 잘못된 인자 오류 · 상한 · 출력 칸(개인정보 칸 없음).
 */
class CommunityMcpToolsTest {

    private static final int MAX = 50;
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0, 0, 123_456_000);

    private final ReviewQueryService reviewQueries = mock(ReviewQueryService.class);
    private final CommunityDeadLetterService deadLetters = mock(CommunityDeadLetterService.class);
    private final ReviewModerationAdminService moderation = mock(ReviewModerationAdminService.class);
    private final ReplicaHealthService replicaHealth = mock(ReplicaHealthService.class);
    private final CommunityMcpTools tools = CommunityMcpTools.of(reviewQueries, deadLetters, moderation, replicaHealth,
        MAX);

    @Test
    @DisplayName("도구 다섯 개를 이름 · 설명 · 객체 입력 스키마와 함께 정해진 순서로 낸다")
    void definitions() {
        assertThat(tools.tools()).extracting(CommunityMcpTool::name).containsExactly("review_summary", "dead_letters",
            "dead_letter_detail", "moderation_queue", "replica_health");
        assertThat(tools.tools()).allSatisfy(tool -> {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.inputSchema()).containsEntry("type", "object").containsKey("properties");
        });
    }

    @Test
    @DisplayName("review_summary: 상품 ID로 공개 리뷰 기준 수 · 평균 · 핏 분포")
    void reviewSummary() {
        when(reviewQueries.summary("14")).thenReturn(new ReviewQueryService.ProductSummary(14L, 3,
            new BigDecimal("4.3"), new ReviewQueryService.FitSummary(1, 2, 0)));

        ToolOutcome outcome = tools.call("review_summary", Map.of("productId", "14"));

        assertThat(outcome.error()).isFalse();
        assertThat(outcome.structured()).containsEntry("productId", "14").containsEntry("reviewCount", 3L)
            .containsEntry("averageRating", new BigDecimal("4.3"))
            .containsEntry("fitSummary", Map.of("small", 1L, "trueToSize", 2L, "large", 0L));
        when(reviewQueries.summary("15")).thenReturn(new ReviewQueryService.ProductSummary(15L, 0, null,
            new ReviewQueryService.FitSummary(0, 0, 0)));
        assertThat(tools.call("review_summary", Map.of("productId", 15)).structured()).containsEntry("productId", "15");
    }

    @Test
    @DisplayName("review_summary: 상품 ID가 없거나 틀리면 도구 오류(isError, 서버 오류 아님)")
    void reviewSummaryErrors() {
        when(reviewQueries.summary(anyString()))
            .thenThrow(new BusinessException(ReviewErrorCode.INVALID_PRODUCT_ID));

        assertThat(tools.call("review_summary", Map.of())).satisfies(outcome -> {
            assertThat(outcome.error()).isTrue();
            assertThat(outcome.message()).contains("productId");
        });
        assertThat(tools.call("review_summary", Map.of("productId", "abc")).error()).isTrue();
        assertThat(tools.call("review_summary", Map.of("productId", 1.5)).error()).isTrue();
        assertThat(tools.call("review_summary", Map.of("productId", true)).error()).isTrue();
    }

    @Test
    @DisplayName("dead_letters: 상태 · 쪽 · 크기(상한으로 줄임), 항목은 ID · 원래 토픽 · eventType · 상태 · 시도 · 예외 종류 · 시각만")
    void deadLetters() {
        when(deadLetters.list(eq("OPEN"), eq(0), anyInt()))
            .thenAnswer(call -> new PageImpl<>(List.of(summary()), PageRequest.of(0, call.getArgument(2)), 1));

        ToolOutcome outcome = tools.call("dead_letters", Map.of("state", "OPEN", "page", 0, "size", 500));

        verify(deadLetters).list("OPEN", 0, MAX);
        assertThat(outcome.structured()).containsEntry("state", "OPEN").containsEntry("page", 0)
            .containsEntry("size", MAX).containsEntry("totalElements", 1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) ((List<?>) outcome.structured().get("items")).getFirst();
        assertThat(item.keySet()).containsExactlyInAnyOrder("id", "sourceTopic", "eventType", "state", "replayCount",
            "failureType", "createdAt");
        assertThat(item).containsEntry("id", 7L).containsEntry("sourceTopic", "kidly.order.events")
            .containsEntry("eventType", "order-item.purchase-confirmed")
            .containsEntry("failureType", "DeserializationException")
            .containsEntry("createdAt", "2026-11-06T10:00:00.123456");
    }

    @Test
    @DisplayName("dead_letters: 기본은 OPEN · 0쪽 · 20, 음수 쪽 · 1보다 작은 크기 · 숫자 아님 · 없는 상태는 도구 오류")
    void deadLettersArguments() {
        when(deadLetters.list(eq("OPEN"), eq(0), eq(20))).thenReturn(new PageImpl<>(List.of()));
        when(deadLetters.list(eq("CLOSED"), anyInt(), anyInt()))
            .thenThrow(new BusinessException(ReviewErrorCode.INVALID_DEAD_LETTER_STATE));

        assertThat(tools.call("dead_letters", Map.of()).error()).isFalse();
        assertThat(tools.call("dead_letters", Map.of("page", -1)).error()).isTrue();
        assertThat(tools.call("dead_letters", Map.of("size", 0)).error()).isTrue();
        assertThat(tools.call("dead_letters", Map.of("page", "x")).error()).isTrue();
        assertThat(tools.call("dead_letters", Map.of("state", "CLOSED")).error()).isTrue();
        assertThat(tools.call("dead_letters", Map.of("state", 1)).error()).isTrue();
    }

    @Test
    @DisplayName("dead_letter_detail: 재처리 결과까지, 원본 본문 · 예외 메시지 · 처리한 관리자는 없고 자유 문구 칸은 형식이 맞을 때만")
    void deadLetterDetail() {
        when(deadLetters.get(7L)).thenReturn(summary());

        Map<String, Object> detail = tools.call("dead_letter_detail", Map.of("id", 7)).structured();

        assertThat(detail.keySet()).containsExactlyInAnyOrder("id", "dltTopic", "dltPartition", "dltOffset",
            "sourceTopic", "sourcePartition", "sourceOffset", "messageKey", "eventType", "eventId", "failureType",
            "state", "replayCount", "lastReplayedAt", "lastReplayFailureType", "createdAt");
        assertThat(detail).containsEntry("messageKey", null).containsEntry("eventId", null)
            .containsEntry("failureType", "DeserializationException").containsEntry("lastReplayFailureType", null)
            .containsEntry("dltTopic", "kidly.order.events.community.dlt");
    }

    @Test
    @DisplayName("dead_letter_detail: ID가 없거나 양수가 아니면 도구 오류, 없는 DLT도 도구 오류(서비스를 부르지 않거나 오류 제목)")
    void deadLetterDetailErrors() {
        when(deadLetters.get(99L)).thenThrow(new BusinessException(ReviewErrorCode.DEAD_LETTER_NOT_FOUND));

        assertThat(tools.call("dead_letter_detail", Map.of()).error()).isTrue();
        assertThat(tools.call("dead_letter_detail", Map.of("id", 0)).error()).isTrue();
        assertThat(tools.call("dead_letter_detail", Map.of("id", "-3")).error()).isTrue();
        verify(deadLetters, never()).get(0L);
        assertThat(tools.call("dead_letter_detail", Map.of("id", 99))).satisfies(outcome -> {
            assertThat(outcome.error()).isTrue();
            assertThat(outcome.message()).contains(ReviewErrorCode.DEAD_LETTER_NOT_FOUND.title());
        });
    }

    @Test
    @DisplayName("moderation_queue: 상태별 수 · 가장 오래된 대기 · 최근 숨김(ID · 이유 · 시각, 상한)")
    void moderationQueue() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", 2L);
        counts.put("PENDING_EXHAUSTED", 1L);
        when(moderation.overview(MAX)).thenReturn(new ModerationOverview(counts, AT,
            List.of(new RecentHidden(5L, "HATE", AT))));

        Map<String, Object> result = tools.call("moderation_queue", Map.of()).structured();

        assertThat(result).containsEntry("counts", counts).containsEntry("oldestPendingCreatedAt",
            "2026-11-06T10:00:00.123456");
        assertThat((List<?>) result.get("recentHidden")).singleElement()
            .isEqualTo(Map.of("reviewId", 5L, "reason", "HATE", "moderatedAt", "2026-11-06T10:00:00.123456"));
    }

    @Test
    @DisplayName("replica_health: 복사본 수 · 표시 상태별 수 · 마지막 반영 시각(없으면 null)")
    void replicaHealth() {
        when(replicaHealth.health()).thenReturn(new ReplicaHealth(3, Map.of("ON_SALE", 2L, "SOLD_OUT", 1L,
            "SALE_ENDED", 0L), AT, null));

        assertThat(tools.call("replica_health", Map.of()).structured()).containsEntry("total", 3L)
            .containsEntry("byDisplayStatus", Map.of("ON_SALE", 2L, "SOLD_OUT", 1L, "SALE_ENDED", 0L))
            .containsEntry("lastUpdatedAt", "2026-11-06T10:00:00.123456")
            .containsEntry("lastSourceOccurredAt", null);
    }

    @Test
    @DisplayName("모르는 도구 · 예상 못 한 예외는 도구 오류이고 예외 메시지를 내보내지 않는다. 인자가 null이어도 된다")
    void unknownAndUnexpected() {
        when(replicaHealth.health()).thenThrow(new IllegalStateException("secret 홍길동"));

        assertThat(tools.call("drop_reviews", Map.of()).error()).isTrue();
        assertThat(tools.call("replica_health", null)).satisfies(outcome -> {
            assertThat(outcome.error()).isTrue();
            assertThat(outcome.message()).doesNotContain("secret").doesNotContain("홍길동");
        });
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("productId", null);
        assertThat(tools.call("review_summary", nullValue).error()).isTrue();
    }

    private static Summary summary() {
        return new TestSummary();
    }

    private static final class TestSummary implements Summary {

        @Override
        public Long getId() {
            return 7L;
        }

        @Override
        public String getDltTopic() {
            return "kidly.order.events.community.dlt";
        }

        @Override
        public int getDltPartition() {
            return 0;
        }

        @Override
        public long getDltOffset() {
            return 12L;
        }

        @Override
        public String getSourceTopic() {
            return "kidly.order.events";
        }

        @Override
        public Integer getSourcePartition() {
            return 1;
        }

        @Override
        public Long getSourceOffset() {
            return 34L;
        }

        @Override
        public String getMessageKey() {
            return "user@example.com";
        }

        @Override
        public String getEventType() {
            return "order-item.purchase-confirmed";
        }

        @Override
        public String getEventId() {
            return "not-a-uuid 홍길동";
        }

        @Override
        public String getFailureType() {
            return "org.springframework.kafka.support.serializer.DeserializationException";
        }

        @Override
        public State getState() {
            return State.OPEN;
        }

        @Override
        public int getReplayCount() {
            return 2;
        }

        @Override
        public Long getLastReplayedBy() {
            return 99L;
        }

        @Override
        public LocalDateTime getLastReplayedAt() {
            return AT;
        }

        @Override
        public String getLastReplayFailureType() {
            return "java.lang.IllegalStateException: 회원 13 없음";
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return AT;
        }
    }
}
