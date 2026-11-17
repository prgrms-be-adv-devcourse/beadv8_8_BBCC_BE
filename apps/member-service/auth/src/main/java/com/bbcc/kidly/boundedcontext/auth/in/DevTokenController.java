package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.DevTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.DevTokenRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.DevTokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "test", "dev"})
@RestController
@RequiredArgsConstructor
public class DevTokenController {

    private final DevTokenService devTokenService;

    @PostMapping("/api/v1/dev/tokens")
    public DevTokenResponse issue(@Valid @RequestBody DevTokenRequest request) {
        return new DevTokenResponse(devTokenService.issue(request));
    }
}
