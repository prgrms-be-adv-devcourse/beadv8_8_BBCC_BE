package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "child_consent", schema = "childprofile",
    indexes = @Index(name = "idx_child_consent_child_profile_id", columnList = "child_profile_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildConsent extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_profile_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_child_consent_child_profile"))
    private ChildProfile childProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Consenter consenter;

    @Column(name = "has_optional_consent", nullable = false)
    private boolean optionalConsent;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    ChildConsent(ChildProfile childProfile, boolean optionalConsent, LocalDateTime consentedAt) {
        this.childProfile = childProfile;
        this.consenter = Consenter.GUARDIAN;
        this.optionalConsent = optionalConsent;
        this.consentedAt = consentedAt;
    }
}
