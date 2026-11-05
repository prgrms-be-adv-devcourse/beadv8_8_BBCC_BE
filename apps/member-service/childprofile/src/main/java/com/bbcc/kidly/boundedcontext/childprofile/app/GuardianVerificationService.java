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
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자가 첫 자녀를 등록하기 전에 받는 휴대폰 본인인증을 저장한다. 인증 결과는 클라이언트가 보낸 값이 아니라 제공사에 다시 조회한 값을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class GuardianVerificationService {
    private final GuardianVerificationRepository repository;
    private final IdentityVerificationClient client;
    private final IdentityKeyGenerator keyGenerator;

    // 포트원을 부르는 동안 DB 연결을 쥐지 않도록 트랜잭션을 걸지 않는다. 동시 요청은 유니크 제약이 막는다
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

    // 탈퇴한 회원의 개인정보를 남기지 않고, 같은 사람이 다시 가입해 인증할 수 있게 지운다
    @Transactional
    public void deleteByMemberId(Long memberId) {
        repository.deleteByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public GuardianVerificationResult find(Long memberId) {
        return repository.findByMemberId(memberId).map(GuardianVerificationResult::from)
            .orElseGet(() -> new GuardianVerificationResult(false, null));
    }
}
