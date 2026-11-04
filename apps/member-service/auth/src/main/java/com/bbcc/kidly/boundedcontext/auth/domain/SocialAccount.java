package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인에 쓰는 소셜 계정. 같은 (provider, providerUserId)는 한 행뿐이고 한 회원에게만 연결된다.
 */
@Entity
@Table(
    name = "social_account",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_social_account_provider_user",
        columnNames = {"provider", "provider_user_id"}),
    indexes = @Index(name = "idx_social_account_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Embedded
    private SocialIdentity identity;

    @Column(nullable = false)
    private LocalDateTime connectedAt;

    private SocialAccount(SocialIdentity identity, LocalDateTime connectedAt) {
        this.identity = identity;
        this.connectedAt = connectedAt;
    }

    public static SocialAccount claim(SocialIdentity identity, LocalDateTime now) {
        return new SocialAccount(identity, now);
    }

    public String registrationKey() {
        return "social-account:" + id;
    }

    public Optional<Long> linkedMemberId() {
        return Optional.ofNullable(memberId);
    }
}
