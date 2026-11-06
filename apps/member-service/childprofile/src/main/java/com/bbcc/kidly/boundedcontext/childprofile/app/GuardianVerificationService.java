package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.IdentityVerificationClient;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.GuardianVerificationResult;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuardianVerificationService {
    private final GuardianVerificationRepository repository;
    private final IdentityVerificationClient client;
    private final IdentityKeyGenerator keyGenerator;

    @Transactional(propagation = Propagation.NEVER)
    public GuardianVerificationResult verify(Long memberId, String id) {
        if (repository.existsByMemberId(memberId)) {
            throw new BusinessException(ChildProfileErrorCode.ALREADY_VERIFIED);
        }
        var identity = client.verify(id);
        var key = keyGenerator.generate(identity);
        var verification = GuardianVerification.create(memberId, key, id, identity, LocalDate.now());
        if (repository.existsByIdentityKey(key)) {
            throw new BusinessException(ChildProfileErrorCode.IDENTITY_ALREADY_VERIFIED);
        }
        return GuardianVerificationResult.from(repository.save(verification));
    }

    @Transactional(readOnly = true)
    public GuardianVerificationResult find(Long memberId) {
        return repository.findByMemberId(memberId).map(GuardianVerificationResult::from)
            .orElseGet(() -> new GuardianVerificationResult(false, null));
    }
}
