package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
    name = "child_profile",
    schema = "childprofile",
    indexes = @Index(name = "idx_child_profile_member_id", columnList = "member_id"))
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfile extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = ChildProfilePolicy.NAME_MAX_LENGTH)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type")
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_fit")
    private PreferredFit preferredFit;

    @Column(name = "notice_confirmed_at")
    private LocalDateTime noticeConfirmedAt;

    // 단방향(@JoinColumn)이면 이력 1건을 추가할 때 기존 이력을 전부 불러오므로 mappedBy로 둔다
    @OneToMany(mappedBy = "childProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt ASC, id ASC")
    private List<GrowthRecord> growthRecords = new ArrayList<>();

    @OneToMany(mappedBy = "childProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("consentedAt ASC, id ASC")
    private List<ChildConsent> consents = new ArrayList<>();

    private ChildProfile(Long memberId) {
        this.memberId = memberId;
    }

    public static ChildProfile register(ChildProfileRegisterInfo info, LocalDateTime now) {
        var age = Period.between(info.birthDate(), now.toLocalDate()).getYears();
        if (age >= ChildProfilePolicy.AGE_LIMIT) {
            throw new BusinessException(ChildProfileErrorCode.CHILD_TOO_OLD);
        }
        validateOptionalConsent(info.optionalConsent(), info.bodyType(), info.preferredFit());
        var child = new ChildProfile(info.memberId());
        child.consents.add(new ChildConsent(child, info.optionalConsent(), now));
        child.change(info.name(), info.birthDate(), info.gender(), info.heightCm(), info.weightKg(),
            info.bodyType(), info.preferredFit());
        child.growthRecords.add(new GrowthRecord(child, info.heightCm(), info.bodyType(), now));
        return child;
    }

    public void update(ChildProfileUpdateInfo info, LocalDateTime now) {
        validateOptionalConsent(hasOptionalConsent(), info.bodyType(), info.preferredFit());
        boolean grown = this.heightCm.compareTo(info.heightCm()) != 0
            || !Objects.equals(this.bodyType, info.bodyType());
        change(info.name(), info.birthDate(), info.gender(), info.heightCm(), info.weightKg(),
            info.bodyType(), info.preferredFit());
        if (grown) {
            growthRecords.add(new GrowthRecord(this, info.heightCm(), info.bodyType(), now));
        }
    }

    public boolean hasOptionalConsent() {
        return !consents.isEmpty() && consents.getLast().isOptionalConsent();
    }

    public List<ChildConsent> getConsents() {
        return Collections.unmodifiableList(consents);
    }

    private static void validateOptionalConsent(boolean consent, BodyType bodyType, PreferredFit preferredFit) {
        if (!consent && (bodyType != null || preferredFit != null)) {
            throw new BusinessException(ChildProfileErrorCode.OPTIONAL_CONSENT_REQUIRED);
        }
    }

    public void confirmNotice(LocalDateTime now) {
        if (noticeConfirmedAt == null) {
            noticeConfirmedAt = now;
        }
    }

    public boolean isNoticeConfirmed() {
        return noticeConfirmedAt != null;
    }

    public int growthRecordCount() {
        return growthRecords.size();
    }

    public LocalDateTime lastRecordedAt() {
        return growthRecords.getLast().getRecordedAt();
    }

    public List<GrowthRecord> getGrowthRecords() {
        return Collections.unmodifiableList(growthRecords);
    }

    private void change(String name, LocalDate birthDate, Gender gender, BigDecimal heightCm, BigDecimal weightKg,
        BodyType bodyType, PreferredFit preferredFit) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.bodyType = bodyType;
        this.preferredFit = preferredFit;
    }
}
