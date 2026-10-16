package com.bbcc.kidly.boundedcontext.member.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.springframework.util.Assert;

/**
 * 소셜 제공자에서 받은 식별자(값 객체). 같은 식별자는 한 회원에게만 연결된다(유니크 제약).
 */
@Embeddable
public record SocialIdentity(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    OAuthProvider provider,
    @Column(name = "provider_user_id", nullable = false)
    String providerUserId) {

    public SocialIdentity {
        requireNonNull(provider, "provider는 필수입니다");
        requireNonNull(providerUserId, "providerUserId는 필수입니다");
        Assert.hasText(providerUserId, "providerUserId는 비어 있을 수 없습니다");
    }
}
