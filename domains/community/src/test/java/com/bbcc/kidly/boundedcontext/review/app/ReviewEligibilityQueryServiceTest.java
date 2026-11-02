package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import com.bbcc.kidly.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Limit;

class ReviewEligibilityQueryServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.now().minusDays(1);

    private final FakeRows rows = new FakeRows();
    private final ReviewEligibilityQueryService service = new ReviewEligibilityQueryService(rows);

    @Test
    @DisplayName("size가 없으면 20건을 읽고(다음 쪽 판단용 1건 더), 커서가 없으면 첫 쪽을 읽는다")
    void defaultSize() {
        service.findEligible(1L, null, null);

        assertThat(rows.lastLimit).isEqualTo(21);
        assertThat(rows.lastAfter).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "50"})
    @DisplayName("size 1 · 50(경계)은 받는다")
    void sizeBoundsAccepted(String size) {
        service.findEligible(1L, null, size);

        assertThat(rows.lastLimit).isEqualTo(Integer.parseInt(size) + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1", "abc", ""})
    @DisplayName("size가 1~50 정수가 아니면 INVALID_PAGE_SIZE")
    void invalidSize(String size) {
        assertThatThrownBy(() -> service.findEligible(1L, null, size))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.INVALID_PAGE_SIZE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!", "bm90LWEtY3Vyc29y", ""})
    @DisplayName("읽을 수 없는 커서는 INVALID_CURSOR")
    void invalidCursor(String cursor) {
        assertThatThrownBy(() -> service.findEligible(1L, cursor, null))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.INVALID_CURSOR));
    }

    @Test
    @DisplayName("size보다 많이 있으면 size건과 마지막 항목의 커서를 주고, 그 커서로 다음 쪽을 그 항목 뒤부터 읽는다")
    void nextCursorRoundTrip() {
        rows.add(3);

        ReviewEligibilityQueryService.EligiblePage first = service.findEligible(1L, null, "2");

        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();
        service.findEligible(1L, first.nextCursor(), "2");
        assertThat(rows.lastAfter).isEqualTo(new Object[] {first.items().get(1).confirmedAt(),
            first.items().get(1).id()});
    }

    @Test
    @DisplayName("size 이하만 있으면 nextCursor는 null이다")
    void lastPage() {
        rows.add(2);

        ReviewEligibilityQueryService.EligiblePage page = service.findEligible(1L, null, "2");

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서는 불투명 문자열이다(안의 값을 URL에 그대로 드러내지 않는다)")
    void cursorIsOpaque() {
        rows.add(3);

        String cursor = service.findEligible(1L, null, "1").nextCursor();

        assertThat(cursor).doesNotContain(":").matches("[A-Za-z0-9_-]+");
        assertThat(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)).isNotBlank();
    }

    private static final class FakeRows implements ReviewEligibilities {

        private final List<EligibleRow> data = new ArrayList<>();
        private int lastLimit;
        private Object[] lastAfter;

        void add(int count) {
            for (int i = 0; i < count; i++) {
                long id = 100L - i;
                LocalDateTime at = BASE.minusMinutes(i);
                data.add(new Row(id, at));
            }
        }

        @Override
        public boolean existsByOrderItemId(Long orderItemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReviewEligibility saveAndFlush(ReviewEligibility eligibility) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EligibleRow> findEligible(Long memberId, Limit limit) {
            lastLimit = limit.max();
            lastAfter = null;
            return data.stream().limit(limit.max()).toList();
        }

        @Override
        public List<EligibleRow> findEligibleAfter(Long memberId, LocalDateTime confirmedAt, Long id, Limit limit) {
            lastLimit = limit.max();
            lastAfter = new Object[] {confirmedAt, id};
            return List.of();
        }
    }

    private record Row(Long id, LocalDateTime at) implements ReviewEligibilities.EligibleRow {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getOrderItemId() {
            return id;
        }

        @Override
        public Long getOrderId() {
            return 1L;
        }

        @Override
        public Long getProductId() {
            return 2L;
        }

        @Override
        public String getProductName() {
            return null;
        }

        @Override
        public String getThumbnailUrl() {
            return null;
        }

        @Override
        public String getSize() {
            return null;
        }

        @Override
        public LocalDateTime getConfirmedAt() {
            return at;
        }
    }
}
