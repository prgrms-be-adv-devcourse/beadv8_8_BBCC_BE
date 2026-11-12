package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.DevGuardianVerificationSeedService;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.DevGuardianVerificationSeedResponse;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 더미 회원 대량 생성 스크립트용. local · test · dev 프로필에서만 등록된다. 운영에 열리면 누구나 본인인증을 건너뛸 수 있다.
 * 이미 기록이 있으면 그대로 두고 200으로 돌려주므로 몇 번을 불러도 같다.
 */
@Profile({"local", "test", "dev"})
@RestController
@RequiredArgsConstructor
public class DevGuardianVerificationController {

    private final DevGuardianVerificationSeedService service;

    @PostMapping("/api/v1/dev/guardian-verifications")
    public DevGuardianVerificationSeedResponse seed(@LoginMember AuthMember member) {
        return DevGuardianVerificationSeedResponse.from(service.seed(member.id()));
    }
}
