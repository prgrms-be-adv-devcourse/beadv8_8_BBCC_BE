package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 로그인에 쓰는 소셜 계정. 같은 (provider, providerUserId)는 한 회원에게만 연결된다.
 */
@Entity
@Table(
    name = "social_account",
    schema = "member",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_social_account_provider_user",
        columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(nullable = false)
    private LocalDateTime connectedAt;

    SocialAccount(Member member, OAuthProvider provider, String providerUserId, LocalDateTime connectedAt) {
        this.member = member;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.connectedAt = connectedAt;
    }
}
