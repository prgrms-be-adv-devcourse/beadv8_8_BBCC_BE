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

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationSubmitInfo;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("필수 값이 없으면 신청할 수 없다")
    void submitRejectsNull() {
        assertThatThrownBy(() -> SellerApplication.submit(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            null, APPLICANT_NICKNAME, BUSINESS_NAME, REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, null, BUSINESS_NAME, REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, null, REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, BUSINESS_NAME, null, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, BUSINESS_NAME, REPRESENTATIVE_NAME, null)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("닉네임 · 상호명 · 대표자명이 비어 있으면 신청할 수 없다")
    void submitRejectsBlankName() {
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, " ", BUSINESS_NAME, REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, " ", REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, BUSINESS_NAME, " ", BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("상호명 · 대표자명은 50자까지다")
    void submitRejectsLongName() {
        var fifty = "가".repeat(SellerApplication.NAME_MAX_LENGTH);
        var fiftyOne = fifty + "가";

        assertThat(SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, fifty, fifty, BUSINESS_REGISTRATION_NUMBER))
            .getBusinessName()).isEqualTo(fifty);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, fiftyOne, REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, BUSINESS_NAME, fiftyOne, BUSINESS_REGISTRATION_NUMBER)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"214-86-12345", "214861234", "21486123456", "21486abcde", "２１４８６１２３４５"})
    @DisplayName("사업자등록번호는 하이픈 없는 숫자 10자리만 받는다")
    void submitRejectsInvalidBusinessRegistrationNumber(String number) {
        assertThatThrownBy(() -> SellerApplication.submit(new SellerApplicationSubmitInfo(
            APPLICANT_ID, APPLICANT_NICKNAME, BUSINESS_NAME, REPRESENTATIVE_NAME, number)))
            .isInstanceOf(IllegalArgumentException.class);
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
    @DisplayName("승인한 관리자와 시각이 없으면 승인할 수 없다")
    void approveRejectsNull() {
        var application = SellerFixture.application(APPLICANT_ID);

        assertThatThrownBy(() -> application.approve(null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> application.approve(ADMIN_ID, null)).isInstanceOf(NullPointerException.class);
        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
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
    @DisplayName("거절한 관리자 · 사유 · 시각이 없으면 거절할 수 없다")
    void rejectRejectsNull() {
        var application = SellerFixture.application(APPLICANT_ID);

        assertThatThrownBy(() -> application.reject(null, REJECT_REASON, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> application.reject(ADMIN_ID, null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> application.reject(ADMIN_ID, REJECT_REASON, null))
            .isInstanceOf(NullPointerException.class);
        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(application.getRejectReason()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("거절 사유가 비어 있으면 거절할 수 없다")
    void rejectRejectsBlankReason(String reason) {
        var application = SellerFixture.application(APPLICANT_ID);

        assertThatThrownBy(() -> application.reject(ADMIN_ID, reason, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(application.getStatus()).isEqualTo(SellerApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("거절 사유는 200자까지다")
    void rejectRejectsLongReason() {
        var application = SellerFixture.application(APPLICANT_ID);
        var maxReason = "가".repeat(SellerApplication.REJECT_REASON_MAX_LENGTH);

        assertThatThrownBy(() -> application.reject(ADMIN_ID, maxReason + "가", NOW))
            .isInstanceOf(IllegalArgumentException.class);
        application.reject(ADMIN_ID, maxReason, NOW);
        assertThat(application.getRejectReason()).isEqualTo(maxReason);
    }
}
