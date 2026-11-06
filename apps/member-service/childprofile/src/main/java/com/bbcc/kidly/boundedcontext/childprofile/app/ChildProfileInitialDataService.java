package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileInitialDataService {

    private static final Long PARENT_ID = 1L;

    private final ChildProfileService childProfileService;
    private final ChildProfileRepository childProfileRepository;
    private final GuardianVerificationRepository verificationRepository;
    private final IdentityKeyGenerator keyGenerator;

    public void load() {
        if (childProfileRepository.count() > 0) {
            log.info("자녀가 이미 있어 자녀 초기 데이터를 넣지 않는다");
            return;
        }
        if (!verificationRepository.existsByMemberId(PARENT_ID)) {
            var identity = new VerifiedIdentity("테스트 보호자", "01000000000", LocalDate.of(1990, 1, 1),
                LocalDateTime.now());
            verificationRepository.save(GuardianVerification.create(PARENT_ID, keyGenerator.generate(identity),
                "seed-guardian-verification", identity, LocalDate.now()));
        }
        childProfileService.register(PARENT_ID, new ChildProfileRequest("테스트", LocalDate.of(2019, 1, 1),
            Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"), BodyType.NORMAL,
            PreferredFit.LOOSE, true, true));
        log.info("자녀 초기 데이터를 넣었다");
    }
}
