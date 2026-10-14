package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원의 역할을 담아 토큰을 발급한다. 역할은 {@link Member#roles()}로 계산한다. SELLER는 저장하지 않으므로 발급할 때마다 판매자 상태 복사본으로 정해진다.
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final MemberRepository memberRepository;
    private final AccessTokenIssuer accessTokenIssuer;

    public String issueAccessToken(Long memberId) {
        var member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return accessTokenIssuer.issue(memberId, member.getNickname(), member.roles());
    }
}
