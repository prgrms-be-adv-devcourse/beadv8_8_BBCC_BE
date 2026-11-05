package com.bbcc.kidly.boundedcontext.childprofile.app.port;

import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;

public interface IdentityVerificationClient {
    VerifiedIdentity verify(String identityVerificationId);
}
