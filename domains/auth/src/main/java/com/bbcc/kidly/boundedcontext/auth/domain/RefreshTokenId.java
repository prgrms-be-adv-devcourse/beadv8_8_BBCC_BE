package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;

@Embeddable
public record RefreshTokenId(
    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    PrincipalType type,
    @Column(name = "principal_id", nullable = false)
    Long principalId) implements Serializable {

    public PrincipalId toPrincipalId() {
        return new PrincipalId(type, principalId);
    }
}
