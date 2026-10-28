package com.bbcc.kidly.boundedcontext.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

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
}
