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

    private String applicantNickname;

    private LocalDateTime applicantNicknameChangedAt;

    @Column(nullable = false, length = SellerPolicy.NAME_MAX_LENGTH)
    private String businessName;

    @Column(length = SellerPolicy.NAME_MAX_LENGTH)
    private String representativeName;

    @Column(length = SellerPolicy.BUSINESS_REGISTRATION_NUMBER_LENGTH)
    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerApplicationStatus status;

    private LocalDateTime applicantWithdrawnAt;

    @Column(length = SellerPolicy.REJECT_REASON_MAX_LENGTH)
    private String rejectReason;

    private Long decidedBy;

    private LocalDateTime decidedAt;

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

    public void clearPersonalInfo(LocalDateTime withdrawnAt) {
        this.representativeName = null;
        this.businessRegistrationNumber = null;
        this.applicantNickname = null;
        this.applicantWithdrawnAt = withdrawnAt;
    }

    private void decide(SellerApplicationStatus decision, Long adminId, LocalDateTime now) {
        if (status != SellerApplicationStatus.PENDING) {
            throw new BusinessException(SellerErrorCode.APPLICATION_NOT_PENDING);
        }
        this.status = decision;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    public String maskedBusinessRegistrationNumber() {
        return Optional.ofNullable(businessRegistrationNumber)
            .map(SellerPolicy::maskBusinessRegistrationNumber)
            .orElse(null);
    }

    public boolean isApproved() {
        return status == SellerApplicationStatus.APPROVED;
    }
}
