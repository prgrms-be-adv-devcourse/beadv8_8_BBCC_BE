package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.ADMIN_ID;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.BUSINESS_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerSuspension;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SellerTest {

    private static final Long APPLICANT_ID = 1L;
    private static final String REASON = "약관 위반";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 10, 0);

    @Test
    @DisplayName("승인된 신청으로 신청자 ID를 그대로 쓰는 ACTIVE 판매자를 만든다")
    void from() {
        var seller = Seller.from(SellerFixture.approvedApplication(APPLICANT_ID));

        assertThat(seller.getId()).isEqualTo(APPLICANT_ID);
        assertThat(seller.getBusinessName()).isEqualTo(BUSINESS_NAME);
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("승인되지 않은 신청으로는 판매자를 만들 수 없다")
    void fromRejectsPending() {
        assertThatThrownBy(() -> Seller.from(SellerFixture.application(APPLICANT_ID)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("신청이 없으면 판매자를 만들 수 없다")
    void fromRejectsNull() {
        assertThatThrownBy(() -> Seller.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("정지하면 SUSPENDED이고 사유 · 관리자 · 시각이 저장된다")
    void suspend() {
        var seller = SellerFixture.activeSeller(APPLICANT_ID);

        seller.suspend(ADMIN_ID, REASON, NOW);

        assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(seller.getSuspension()).isEqualTo(new SellerSuspension(REASON, ADMIN_ID, NOW));
    }

    @Test
    @DisplayName("이미 정지된 판매자는 다시 정지할 수 없다")
    void suspendRejectsSuspended() {
        var seller = SellerFixture.activeSeller(APPLICANT_ID);
        seller.suspend(ADMIN_ID, REASON, NOW);

        assertThatThrownBy(() -> seller.suspend(ADMIN_ID + 1, "다른 사유", NOW.plusMinutes(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_ALREADY_SUSPENDED));
        assertThat(seller.getSuspension()).isEqualTo(new SellerSuspension(REASON, ADMIN_ID, NOW));
    }

    @Test
    @DisplayName("정지한 관리자 · 사유 · 시각이 없으면 정지할 수 없다")
    void suspendRejectsNull() {
        var seller = SellerFixture.activeSeller(APPLICANT_ID);

        assertThatThrownBy(() -> seller.suspend(null, REASON, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> seller.suspend(ADMIN_ID, null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> seller.suspend(ADMIN_ID, REASON, null)).isInstanceOf(NullPointerException.class);
        assertStillActive(seller);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("정지 사유가 비어 있으면 정지할 수 없다")
    void suspendRejectsBlankReason(String reason) {
        var seller = SellerFixture.activeSeller(APPLICANT_ID);

        assertThatThrownBy(() -> seller.suspend(ADMIN_ID, reason, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertStillActive(seller);
    }

    @Test
    @DisplayName("정지 사유는 200자까지다")
    void suspendRejectsLongReason() {
        var seller = SellerFixture.activeSeller(APPLICANT_ID);
        var maxReason = "가".repeat(SellerSuspension.REASON_MAX_LENGTH);

        assertThatThrownBy(() -> seller.suspend(ADMIN_ID, maxReason + "가", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertStillActive(seller);
        seller.suspend(ADMIN_ID, maxReason, NOW);
        assertThat(seller.getSuspension().reason()).isEqualTo(maxReason);
    }

    private void assertStillActive(Seller seller) {
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.getSuspension()).isNull();
    }
}
