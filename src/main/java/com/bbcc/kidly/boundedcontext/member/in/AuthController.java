package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.in.dto.LoginResponse;
import com.bbcc.kidly.global.security.PublicApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginService socialLoginService;
    private final AuthTokenService authTokenService;

    // 프론트가 소셜 제공자에서 받은 인가 코드로 로그인한다. 처음이면 가입된다
    @PublicApi
    @PostMapping("/api/v1/auth/login/{provider}")
    public LoginResponse login(@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        var member = socialLoginService.login(OAuthProvider.from(provider), request);
        return new LoginResponse(authTokenService.issueAccessToken(member.getId()));
    }
}
