package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.IdentityVerificationClient;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 통합 · 기능 테스트에서 포트원 대신 쓴다. 정해 둔 ID가 아니면 모두 같은 사람으로 인증된다.
 * 실제 포트원 응답 해석은 PortOneIdentityVerificationClientTest가 확인한다.
 */
@Primary
@Component
public class FakeIdentityVerificationClient implements IdentityVerificationClient {

    public static final String READY_ID = "ready";
    public static final String UNDERAGE_ID = "underage";
    public static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 11, 5, 10, 49);

    @Override
    public VerifiedIdentity verify(String identityVerificationId) {
        if (identityVerificationId.equals(READY_ID)) {
            throw new BusinessException(ChildProfileErrorCode.VERIFICATION_INCOMPLETE);
        }
        var birthDate = identityVerificationId.equals(UNDERAGE_ID)
            ? LocalDate.now().minusYears(18)
            : LocalDate.of(1990, 1, 1);
        return new VerifiedIdentity("보호자", "01012345678", birthDate, VERIFIED_AT);
    }
}
