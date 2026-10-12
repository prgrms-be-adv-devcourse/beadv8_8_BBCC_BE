package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.out.AccessTokenIssuer;
import com.bbcc.kidly.global.config.DevOnly;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 로컬 개발용 토큰 발급. 회원이 실제로 있는지는 확인하지 않으므로 운영에서는 등록하지 않는다
@DevOnly
@Service
@RequiredArgsConstructor
public class DevTokenService {

    private final AccessTokenIssuer accessTokenIssuer;

    public String issue(Long memberId, List<String> roles) {
        return accessTokenIssuer.issue(memberId, roles);
    }
}
