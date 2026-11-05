package com.bbcc.kidly.boundedcontext.childprofile.domain;

import java.util.Optional;

public interface GuardianVerificationRepository {
    GuardianVerification save(GuardianVerification verification);

    Optional<GuardianVerification> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    boolean existsByIdentityKey(String identityKey);
}
