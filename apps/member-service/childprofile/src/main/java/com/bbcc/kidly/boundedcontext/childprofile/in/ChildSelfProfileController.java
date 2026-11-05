package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildProfileResponse;
import com.bbcc.kidly.member.support.security.AuthChild;
import com.bbcc.kidly.member.support.security.LoginChild;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/child")
@RequiredArgsConstructor
public class ChildSelfProfileController {
    private final ChildProfileService service;

    @GetMapping("/profile")
    public ChildProfileResponse get(@LoginChild AuthChild child) {
        return ChildProfileResponse.from(service.getOwnProfile(child.accountId(), child.guardianId()));
    }
}
