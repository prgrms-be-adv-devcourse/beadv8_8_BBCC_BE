package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 신청. 회원이 신청하면 PENDING이고, 관리자가 승인하면 APPROVED, 거절하면 REJECTED가 된다.
 * 신청자는 {@code applicantId}(회원 ID) 값으로만 가리킨다. 거절된 회원의 재신청은 새 행으로 쌓인다.
 * 신청한 회원이 탈퇴하면 개인정보(대표자명 · 사업자등록번호 · 신청자 닉네임)만 비우고 행은 남긴다.
 */
@Entity
@Table(
    name = "seller_application",
    schema = "seller",
    indexes = @Index(name = "idx_seller_application_applicant_id", columnList = "applicant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long applicantId;

    // 관리자 목록 표시용. 신청 시점 닉네임으로 저장하고, 회원이 닉네임을 바꾸면 이벤트로 갱신한다. 탈퇴한 회원이면 null
    private String applicantNickname;

    // 마지막으로 반영한 닉네임 변경 시각. 바꾼 적이 없으면 null
    private LocalDateTime applicantNicknameChangedAt;

    @Column(nullable = false, length = SellerPolicy.NAME_MAX_LENGTH)
    private String businessName;

    // 탈퇴한 회원이면 null
    @Column(length = SellerPolicy.NAME_MAX_LENGTH)
    private String representativeName;

    // 탈퇴한 회원이면 null
    @Column(length = SellerPolicy.BUSINESS_REGISTRATION_NUMBER_LENGTH)
    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerApplicationStatus status;

    // 신청한 회원이 탈퇴한 시각. 탈퇴하지 않았으면 null
    private LocalDateTime applicantWithdrawnAt;

    // 거절된 신청만 값이 있다. 신청한 회원에게 보여 준다
    @Column(length = SellerPolicy.REJECT_REASON_MAX_LENGTH)
    private String rejectReason;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    // 승인과 거절이 동시에 오면 늦게 커밋하는 쪽이 실패한다(409)
    @Version
    private Long version;

    private SellerApplication(SellerApplicationSubmitInfo info) {
        this.applicantId = info.applicantId();
        this.applicantNickname = info.applicantNickname();
        this.businessName = info.businessName();
        this.representativeName = info.representativeName();
        this.businessRegistrationNumber = info.businessRegistrationNumber();
        this.status = SellerApplicationStatus.PENDING;
    }

    public static SellerApplication submit(SellerApplicationSubmitInfo info) {
        return new SellerApplication(info);
    }

    // 탈퇴한 회원은 판매자가 될 수 없다. 거절은 심사 대기 목록을 정리할 수 있게 막지 않는다
    public void approve(Long adminId, LocalDateTime now) {
        if (applicantWithdrawnAt != null) {
            throw new BusinessException(SellerErrorCode.APPLICANT_WITHDRAWN);
        }
        decide(SellerApplicationStatus.APPROVED, adminId, now);
    }

    public void reject(Long adminId, String reason, LocalDateTime now) {
        decide(SellerApplicationStatus.REJECTED, adminId, now);
        this.rejectReason = reason;
    }

    // 회원 변경 이벤트로 갱신한다. 저장된 변경보다 늦게 일어난 변경만 반영한다
    // 탈퇴한 회원은 닉네임을 바꿀 수 없으므로 탈퇴 뒤에 온 변경은 탈퇴 전 것이라 반영하지 않는다
    public void changeApplicantNickname(String nickname, LocalDateTime changedAt) {
        if (applicantWithdrawnAt != null) {
            return;
        }
        if (applicantNicknameChangedAt != null && !changedAt.isAfter(applicantNicknameChangedAt)) {
            return;
        }
        this.applicantNickname = nickname;
        this.applicantNicknameChangedAt = changedAt;
    }

    // 신청한 회원이 탈퇴하면 개인정보를 비우고 탈퇴 시각을 남긴다
    public void clearPersonalInfo(LocalDateTime withdrawnAt) {
        this.representativeName = null;
        this.businessRegistrationNumber = null;
        this.applicantNickname = null;
        this.applicantWithdrawnAt = withdrawnAt;
    }

    // 승인 · 거절은 PENDING일 때만 할 수 있다
    private void decide(SellerApplicationStatus decision, Long adminId, LocalDateTime now) {
        if (status != SellerApplicationStatus.PENDING) {
            throw new BusinessException(SellerErrorCode.APPLICATION_NOT_PENDING);
        }
        this.status = decision;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    // 탈퇴한 회원의 신청이면 null
    public String maskedBusinessRegistrationNumber() {
        return Optional.ofNullable(businessRegistrationNumber)
            .map(SellerPolicy::maskBusinessRegistrationNumber)
            .orElse(null);
    }

    public boolean isApproved() {
        return status == SellerApplicationStatus.APPROVED;
    }
}
