package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.BUSINESS_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerTest {

    private static final Long APPLICANT_ID = 1L;

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
}
