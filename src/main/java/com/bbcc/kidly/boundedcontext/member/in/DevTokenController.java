package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.DevTokenService;
import com.bbcc.kidly.boundedcontext.member.in.dto.DevTokenRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.DevTokenResponse;
import com.bbcc.kidly.global.security.PublicApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 로그인 전까지 Swagger · Postman에서 로그인이 필요한 API를 호출할 토큰을 발급한다.
 * local · test 프로필에서만 등록된다. 운영에 열리면 누구나 ADMIN 토큰을 받을 수 있다.
 */
@Profile({"local", "test"})
@RestController
@RequiredArgsConstructor
public class DevTokenController {

    private final DevTokenService devTokenService;

    @PublicApi
    @PostMapping("/api/v1/dev/tokens")
    public DevTokenResponse issue(@Valid @RequestBody DevTokenRequest request) {
        return new DevTokenResponse(devTokenService.issue(request.memberId(), request.roles()));
    }
}
