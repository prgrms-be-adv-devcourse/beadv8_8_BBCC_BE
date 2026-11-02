package com.bbcc.kidly.gateway.revocation;

import com.bbcc.kidly.shared.auth.PrincipalType;

public record PrincipalKey(PrincipalType type, Long id) {
}
