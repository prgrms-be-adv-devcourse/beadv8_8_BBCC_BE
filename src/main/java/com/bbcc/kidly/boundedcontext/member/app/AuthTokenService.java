package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.security.Role;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원의 역할을 계산해 토큰을 발급한다. SELLER 역할은 저장하지 않고, 회원의 판매자 상태 복사본으로 발급할 때마다 계산한다.
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final MemberRepository memberRepository;
    private final AccessTokenIssuer accessTokenIssuer;

    public String issueAccessToken(Long memberId) {
        var member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return accessTokenIssuer.issue(memberId, rolesOf(member));
    }

    private List<Role> rolesOf(Member member) {
        var roles = new ArrayList<Role>();
        roles.add(Role.BUYER);
        if (member.isActiveSeller()) {
            roles.add(Role.SELLER);
        }
        if (member.isAdmin()) {
            roles.add(Role.ADMIN);
        }
        return List.copyOf(roles);
    }
}
