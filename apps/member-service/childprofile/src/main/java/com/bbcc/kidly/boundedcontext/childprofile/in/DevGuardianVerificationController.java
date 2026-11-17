package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.DevGuardianVerificationSeedService;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.DevGuardianVerificationSeedResponse;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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
