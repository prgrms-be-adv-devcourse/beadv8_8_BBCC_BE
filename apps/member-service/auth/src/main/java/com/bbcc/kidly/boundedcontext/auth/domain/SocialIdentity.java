package com.bbcc.kidly.boundedcontext.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record SocialIdentity(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    OAuthProvider provider,
    @Column(name = "provider_user_id", nullable = false)
    String providerUserId) {
}
