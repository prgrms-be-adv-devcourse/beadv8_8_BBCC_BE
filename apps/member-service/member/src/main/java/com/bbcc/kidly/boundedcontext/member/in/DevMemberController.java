package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.DevMemberSeedService;
import com.bbcc.kidly.boundedcontext.member.app.dto.DevMemberSeedRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.DevMemberSeedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "test", "dev"})
@RestController
@RequiredArgsConstructor
public class DevMemberController {

    private final DevMemberSeedService devMemberSeedService;

    @PostMapping("/api/v1/dev/members")
    public DevMemberSeedResponse seed(@Valid @RequestBody DevMemberSeedRequest request) {
        return DevMemberSeedResponse.from(devMemberSeedService.seed(request));
    }
}
