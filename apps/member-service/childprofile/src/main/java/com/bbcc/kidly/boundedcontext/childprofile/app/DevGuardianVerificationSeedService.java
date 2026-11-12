package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.DevGuardianVerificationSeedResult;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 포트원 본인인증 없이 보호자 본인인증 기록을 만든다. 더미 회원이 자녀를 등록할 수 있게 하려고 둔다. 운영에서는 등록하지 않는다.
 * 클래스에 트랜잭션을 걸지 않는다. 동시 요청이 유니크에 걸려도 롤백된 트랜잭션 밖에서 다시 확인해야 하기 때문이다.
 */
@Profile({"local", "test", "dev"})
@Service
@RequiredArgsConstructor
public class DevGuardianVerificationSeedService {

    // 실제 식별값(HMAC 64자 16진수)과 겹치지 않는 접두어
    private static final String VALUE_PREFIX = "seed-bulk:";

    private final GuardianVerificationRepository repository;

    public DevGuardianVerificationSeedResult seed(Long memberId) {
        if (repository.existsByMemberId(memberId)) {
            return new DevGuardianVerificationSeedResult(memberId, false);
        }
        var value = VALUE_PREFIX + memberId;
        var identity = new VerifiedIdentity("더미 보호자", "01000000000", LocalDate.of(1990, 1, 1),
            LocalDateTime.now());
        try {
            repository.save(GuardianVerification.create(memberId, value, value, identity, LocalDate.now()));
            return new DevGuardianVerificationSeedResult(memberId, true);
        } catch (DataIntegrityViolationException e) {
            if (repository.existsByMemberId(memberId)) {
                return new DevGuardianVerificationSeedResult(memberId, false);
            }
            throw e;
        }
    }
}
