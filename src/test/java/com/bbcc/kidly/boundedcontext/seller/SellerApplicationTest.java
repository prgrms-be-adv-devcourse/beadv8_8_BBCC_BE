package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.ADMIN_ID;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.APPLICANT_NICKNAME;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.BUSINESS_NAME;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.BUSINESS_REGISTRATION_NUMBER;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.NOW;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.REJECT_REASON;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.REPRESENTATIVE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerApplicationTest {

    private static final Long APPLICANT_ID = 1L;

    @Test
    @DisplayName("신청하면 입력한 값으로 PENDING 신청이 만들어진다")
    void submit() {
        var application = SellerFixture.application(APPLICANT_ID);

        assertThat(application.getApplicantId()).isEqualTo(APPLICANT_ID);
        assertThat(application.getApplicantNickname()).isEqualTo(APPLICANT_NICKNAME);
        assertThat(application.getBusinessName()).isEqualTo(BUSINESS_NAME);
        assertThat(application.getRepresentativeName()).isEqualTo(REPRESENTATIVE_NAME);
        assertThat(application.getBusinessRegistrationNumber()).isEqualTo(BUSINESS_REGISTRATION_NUMBER);
        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(application.isApproved()).isFalse();
        assertThat(application.getDecidedBy()).isNull();
        assertThat(application.getDecidedAt()).isNull();
        assertThat(application.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("사업자등록번호는 앞 4자리만 보이게 가려서 돌려준다")
    void maskedBusinessRegistrationNumber() {
        var application = SellerFixture.application(APPLICANT_ID);

        assertThat(application.maskedBusinessRegistrationNumber()).isEqualTo("214-8*-*****");
    }

    @Test
    @DisplayName("승인하면 APPROVED가 되고 승인한 관리자와 시각이 남는다")
    void approve() {
        var application = SellerFixture.application(APPLICANT_ID);

        application.approve(ADMIN_ID, NOW);

        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.APPROVED);
        assertThat(application.isApproved()).isTrue();
        assertThat(application.getDecidedBy()).isEqualTo(ADMIN_ID);
        assertThat(application.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("PENDING이 아닌 신청은 승인할 수 없다")
    void approveRejectsDecided() {
        var application = SellerFixture.approvedApplication(APPLICANT_ID);

        assertThatThrownBy(() -> application.approve(ADMIN_ID, NOW.plusMinutes(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING));
        assertThat(application.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("거절하면 REJECTED가 되고 사유 · 거절한 관리자 · 시각이 남는다")
    void reject() {
        var application = SellerFixture.application(APPLICANT_ID);

        application.reject(ADMIN_ID, REJECT_REASON, NOW);

        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.REJECTED);
        assertThat(application.getRejectReason()).isEqualTo(REJECT_REASON);
        assertThat(application.getDecidedBy()).isEqualTo(ADMIN_ID);
        assertThat(application.getDecidedAt()).isEqualTo(NOW);
        assertThat(application.isApproved()).isFalse();
    }

    @Test
    @DisplayName("PENDING이 아닌 신청은 거절할 수 없다")
    void rejectRejectsDecided() {
        var approved = SellerFixture.approvedApplication(APPLICANT_ID);
        var rejected = SellerFixture.rejectedApplication(APPLICANT_ID);

        assertThatThrownBy(() -> approved.reject(ADMIN_ID, REJECT_REASON, NOW.plusMinutes(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING));
        assertThatThrownBy(() -> rejected.reject(ADMIN_ID, REJECT_REASON, NOW.plusMinutes(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING));
        assertThat(approved.getStatus()).isEqualTo(SellerApplicationStatus.APPROVED);
        assertThat(approved.getRejectReason()).isNull();
        assertThat(rejected.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("거절된 신청은 승인할 수 없다")
    void approveRejectsRejected() {
        var application = SellerFixture.rejectedApplication(APPLICANT_ID);

        assertThatThrownBy(() -> application.approve(ADMIN_ID, NOW.plusMinutes(1)))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING));
        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("신청 회원 닉네임을 처음 바꾸면 반영한다")
    void changeApplicantNickname() {
        var application = SellerFixture.application(1L);

        application.changeApplicantNickname("하은맘", NOW);

        assertThat(application.getApplicantNickname()).isEqualTo("하은맘");
        assertThat(application.getApplicantNicknameChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("저장된 변경보다 늦은 변경만 반영한다")
    void changeApplicantNicknameOnlyLater() {
        var application = SellerFixture.application(1L);
        application.changeApplicantNickname("하은맘", NOW);

        application.changeApplicantNickname("예전닉네임", NOW);
        application.changeApplicantNickname("더예전닉네임", NOW.minusDays(1));
        assertThat(application.getApplicantNickname()).isEqualTo("하은맘");

        application.changeApplicantNickname("새닉네임", NOW.plusDays(1));
        assertThat(application.getApplicantNickname()).isEqualTo("새닉네임");
    }
}
