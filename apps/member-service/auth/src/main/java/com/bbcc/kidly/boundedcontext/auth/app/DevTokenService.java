package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.DevTokenRequest;
import com.bbcc.kidly.boundedcontext.auth.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Profile({"local", "test", "dev"})
@Service
@RequiredArgsConstructor
public class DevTokenService {

    private final AccessTokenIssuer accessTokenIssuer;

    public String issue(DevTokenRequest request) {
        var nickname = StringUtils.hasText(request.nickname()) ? request.nickname() : "회원" + request.memberId();
        return accessTokenIssuer.issue(new TokenSubject.Member(request.memberId(), nickname, request.roles()));
    }
}
