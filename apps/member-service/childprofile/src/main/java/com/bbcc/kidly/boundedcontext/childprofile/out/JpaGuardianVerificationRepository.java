package com.bbcc.kidly.boundedcontext.childprofile.out;

import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaGuardianVerificationRepository
    extends JpaRepository<GuardianVerification, Long>, GuardianVerificationRepository {
}
