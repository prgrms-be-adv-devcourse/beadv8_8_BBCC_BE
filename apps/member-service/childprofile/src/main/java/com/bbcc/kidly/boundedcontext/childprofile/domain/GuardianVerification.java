package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import com.bbcc.kidly.member.support.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guardian_verification", schema = "childprofile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianVerification extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 64)
    private String identityKey;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    @Column(nullable = false, unique = true)
    private String identityVerificationId;

    public static GuardianVerification create(Long memberId, String identityKey, String identityVerificationId,
        VerifiedIdentity identity, LocalDate today) {
        if (Period.between(identity.birthDate(), today).getYears() < ChildProfilePolicy.GUARDIAN_MIN_AGE) {
            throw new BusinessException(ChildProfileErrorCode.GUARDIAN_UNDERAGE);
        }
        var verification = new GuardianVerification();
        verification.memberId = memberId;
        verification.identityKey = identityKey;
        verification.identityVerificationId = identityVerificationId;
        verification.birthDate = identity.birthDate();
        verification.verifiedAt = identity.verifiedAt();
        return verification;
    }
}
