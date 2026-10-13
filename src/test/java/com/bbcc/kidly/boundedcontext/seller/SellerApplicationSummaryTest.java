package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerApplicationSummaryTest {

    @Test
    @DisplayName("신청의 값을 옮기고 사업자등록번호는 가려서 담는다")
    void from() {
        var application = SellerFixture.application(1L);

        var summary = SellerApplicationSummary.from(application);

        assertThat(summary.applicantId()).isEqualTo(1L);
        assertThat(summary.applicantNickname()).isEqualTo(SellerFixture.APPLICANT_NICKNAME);
        assertThat(summary.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(summary.maskedBusinessRegistrationNumber())
            .isEqualTo(SellerFixture.MASKED_BUSINESS_REGISTRATION_NUMBER);
        assertThat(summary.status()).isEqualTo(SellerApplicationStatus.PENDING);
    }
}
