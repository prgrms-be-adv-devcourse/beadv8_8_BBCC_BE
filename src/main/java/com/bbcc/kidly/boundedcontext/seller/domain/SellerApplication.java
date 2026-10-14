package com.bbcc.kidly.boundedcontext.seller.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
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
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 판매자 신청. 회원이 신청하면 PENDING이고, 관리자가 승인하면 APPROVED, 거절하면 REJECTED가 된다.
 * 신청자는 {@code applicantId}(회원 ID) 값으로만 가리킨다. 거절된 회원의 재신청은 새 행으로 쌓인다.
 */
@Entity
@Table(
    name = "seller_application",
    schema = "seller",
    indexes = @Index(name = "idx_seller_application_applicant_id", columnList = "applicant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication extends BaseTimeEntity {

    public static final int NAME_MAX_LENGTH = 50;
    public static final int BUSINESS_REGISTRATION_NUMBER_LENGTH = 10;
    public static final String BUSINESS_REGISTRATION_NUMBER_REGEX = "[0-9]{10}";
    public static final int REJECT_REASON_MAX_LENGTH = 200;

    // 하이픈 없는 ASCII 숫자 10자리. 진위 확인(사업자 인증)은 MVP에서 빠졌다
    private static final Pattern BUSINESS_REGISTRATION_NUMBER = Pattern.compile(BUSINESS_REGISTRATION_NUMBER_REGEX);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long applicantId;

    // 관리자 목록 표시용. 신청 시점 닉네임으로 저장하고, 회원이 닉네임을 바꾸면 이벤트로 갱신한다
    @Column(nullable = false)
    private String applicantNickname;

    // 마지막으로 반영한 닉네임 변경 시각. 바꾼 적이 없으면 null
    private LocalDateTime applicantNicknameChangedAt;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String businessName;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String representativeName;

    @Column(nullable = false, length = BUSINESS_REGISTRATION_NUMBER_LENGTH)
    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerApplicationStatus status;

    // 거절된 신청만 값이 있다. 신청한 회원에게 보여 준다
    @Column(length = REJECT_REASON_MAX_LENGTH)
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
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.applicantId(), "applicantId는 필수입니다");
        var applicantNickname = requireNonNull(info.applicantNickname(), "applicantNickname은 필수입니다");
        var businessName = requireNonNull(info.businessName(), "businessName은 필수입니다");
        var representativeName = requireNonNull(info.representativeName(), "representativeName은 필수입니다");
        var businessRegistrationNumber =
            requireNonNull(info.businessRegistrationNumber(), "businessRegistrationNumber는 필수입니다");
        Assert.hasText(applicantNickname, "신청 회원 닉네임은 비어 있을 수 없습니다");
        Assert.hasText(businessName, "상호명은 비어 있을 수 없습니다");
        Assert.isTrue(businessName.length() <= NAME_MAX_LENGTH, "상호명은 50자 이하여야 합니다");
        Assert.hasText(representativeName, "대표자명은 비어 있을 수 없습니다");
        Assert.isTrue(representativeName.length() <= NAME_MAX_LENGTH, "대표자명은 50자 이하여야 합니다");
        Assert.isTrue(BUSINESS_REGISTRATION_NUMBER.matcher(businessRegistrationNumber).matches(),
            "사업자등록번호는 하이픈 없는 숫자 10자리여야 합니다");
        return new SellerApplication(info);
    }

    public void approve(Long adminId, LocalDateTime now) {
        decide(SellerApplicationStatus.APPROVED, adminId, now);
    }

    public void reject(Long adminId, String reason, LocalDateTime now) {
        requireNonNull(reason, "reason은 필수입니다");
        Assert.hasText(reason, "거절 사유는 비어 있을 수 없습니다");
        Assert.isTrue(reason.length() <= REJECT_REASON_MAX_LENGTH, "거절 사유는 200자 이하여야 합니다");
        decide(SellerApplicationStatus.REJECTED, adminId, now);
        this.rejectReason = reason;
    }

    // 회원 변경 이벤트로 갱신한다. 저장된 변경보다 늦게 일어난 변경만 반영한다
    public void changeApplicantNickname(String nickname, LocalDateTime changedAt) {
        requireNonNull(changedAt, "changedAt은 필수입니다");
        Assert.hasText(nickname, "신청 회원 닉네임은 비어 있을 수 없습니다");
        if (applicantNicknameChangedAt != null && !changedAt.isAfter(applicantNicknameChangedAt)) {
            return;
        }
        this.applicantNickname = nickname;
        this.applicantNicknameChangedAt = changedAt;
    }

    // 승인 · 거절은 PENDING일 때만 할 수 있다
    private void decide(SellerApplicationStatus decision, Long adminId, LocalDateTime now) {
        requireNonNull(adminId, "adminId는 필수입니다");
        requireNonNull(now, "now는 필수입니다");
        if (status != SellerApplicationStatus.PENDING) {
            throw new BusinessException(SellerErrorCode.APPLICATION_NOT_PENDING);
        }
        this.status = decision;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    // 신청 응답 · 관리자 목록용. 앞 4자리만 보이게 가린다 (예: 2148612345 → 214-8*-*****)
    public String maskedBusinessRegistrationNumber() {
        return businessRegistrationNumber.substring(0, 3) + "-" + businessRegistrationNumber.charAt(3) + "*-*****";
    }

    public boolean isApproved() {
        return status == SellerApplicationStatus.APPROVED;
    }
}
