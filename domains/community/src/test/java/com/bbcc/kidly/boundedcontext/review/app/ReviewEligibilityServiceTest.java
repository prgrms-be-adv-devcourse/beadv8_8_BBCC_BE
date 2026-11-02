package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewEligibilities;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewEligibility;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

class ReviewEligibilityServiceTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.now().minusMinutes(5);

    private final FakeReviewEligibilities eligibilities = new FakeReviewEligibilities();
    private final ReviewEligibilityService service =
        new ReviewEligibilityService(eligibilities, new ReviewSeparateTransaction());

    @Test
    @DisplayName("처음 받은 주문상품은 자격을 저장한다")
    void savesFirst() {
        service.record(confirmed(11L));

        assertThat(eligibilities.rows).containsKey(11L);
        assertThat(eligibilities.rows.get(11L).getMemberId()).isEqualTo(13L);
        assertThat(eligibilities.saveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 주문상품이 다시 오면 먼저 조회해 저장하지 않는다(DB 제약까지 가지 않음)")
    void skipsExisting() {
        service.record(confirmed(11L));

        service.record(confirmed(11L));

        assertThat(eligibilities.saveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("조회 뒤 저장이 주문상품 유일 제약(23505)에 막혀도(동시 수신) 다시 조회해 있으면 예외 없이 끝낸다")
    void concurrentDuplicateConverges() {
        eligibilities.insertedByOtherBeforeSave = 11L;

        service.record(confirmed(11L));

        assertThat(eligibilities.rows).containsKey(11L);
        assertThat(eligibilities.saveCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("주문상품 유일 제약이 아닌 위반(NOT NULL 등)은 자격이 있어도 그대로 던진다(재시도 · DLT)")
    void otherViolationPropagates() {
        eligibilities.violation = violation("23502", null);
        eligibilities.insertedByOtherBeforeSave = 11L;

        assertThatThrownBy(() -> service.record(confirmed(11L))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 유일 제약 위반은 그대로 던진다")
    void otherUniqueViolationPropagates() {
        eligibilities.violation = violation("23505", "uk_other");
        eligibilities.insertedByOtherBeforeSave = 11L;

        assertThatThrownBy(() -> service.record(confirmed(11L))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("주문상품 유일 제약 위반이어도 다시 조회해 자격이 없으면 그대로 던진다")
    void duplicateWithoutRowPropagates() {
        eligibilities.violation = violation("23505", ReviewEligibilityService.ORDER_ITEM_UNIQUE);
        eligibilities.alwaysViolate = true;

        assertThatThrownBy(() -> service.record(confirmed(11L))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static DataIntegrityViolationException violation(String sqlState, String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("could not execute statement", new SQLException("violation", sqlState),
                constraint));
    }

    private static PurchaseConfirmed confirmed(long orderItemId) {
        return new PurchaseConfirmed(orderItemId, 12L, 13L, 14L, 15L, "110", CONFIRMED_AT);
    }

    private static final class FakeReviewEligibilities implements ReviewEligibilities {

        private final Map<Long, ReviewEligibility> rows = new HashMap<>();
        private int saveCalls;
        private Long insertedByOtherBeforeSave;
        private boolean alwaysViolate;
        private DataIntegrityViolationException violation =
            violation("23505", ReviewEligibilityService.ORDER_ITEM_UNIQUE);

        @Override
        public boolean existsByOrderItemId(Long orderItemId) {
            return rows.containsKey(orderItemId);
        }

        @Override
        public ReviewEligibility saveAndFlush(ReviewEligibility eligibility) {
            saveCalls++;
            if (alwaysViolate) {
                throw violation;
            }
            if (eligibility.getOrderItemId().equals(insertedByOtherBeforeSave)) {
                rows.put(eligibility.getOrderItemId(), eligibility);
                throw violation;
            }
            rows.put(eligibility.getOrderItemId(), eligibility);
            return eligibility;
        }

        @Override
        public List<EligibleRow> findEligible(Long memberId, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EligibleRow> findEligibleAfter(Long memberId, LocalDateTime confirmedAt, Long id, Limit limit) {
            throw new UnsupportedOperationException();
        }
    }
}
