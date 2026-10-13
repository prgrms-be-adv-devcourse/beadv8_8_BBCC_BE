package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SellerApplicationRepositoryIntegrationTest {

    private static final Long APPLICANT_ID = 1L;
    private static final Long OTHER_APPLICANT_ID = 2L;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Test
    @DisplayName("회원의 가장 최근 신청을 찾는다")
    void findLatest() {
        sellerApplicationRepository.save(SellerFixture.approvedApplication(APPLICANT_ID));
        var latest = sellerApplicationRepository.save(SellerFixture.application(APPLICANT_ID));
        sellerApplicationRepository.save(SellerFixture.application(OTHER_APPLICANT_ID));

        assertThat(sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(APPLICANT_ID))
            .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(latest.getId()));
        assertThat(sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(3L)).isEmpty();
    }

    @Test
    @DisplayName("회원에게 해당 상태의 신청이 있는지 확인한다")
    void existsByStatus() {
        sellerApplicationRepository.save(SellerFixture.approvedApplication(APPLICANT_ID));
        sellerApplicationRepository.save(SellerFixture.application(OTHER_APPLICANT_ID));

        assertThat(sellerApplicationRepository.existsByApplicantIdAndStatus(
            APPLICANT_ID, SellerApplicationStatus.PENDING)).isFalse();
        assertThat(sellerApplicationRepository.existsByApplicantIdAndStatus(
            OTHER_APPLICANT_ID, SellerApplicationStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("저장한 신청은 값과 신청 시각이 그대로 읽히고, 없는 신청은 404다")
    void getById() {
        var saved = sellerApplicationRepository.save(SellerFixture.application(APPLICANT_ID));

        var found = sellerApplicationRepository.getById(saved.getId());
        assertThat(found.getBusinessRegistrationNumber()).isEqualTo(SellerFixture.BUSINESS_REGISTRATION_NUMBER);
        assertThat(found.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> sellerApplicationRepository.getById(saved.getId() + 1))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
