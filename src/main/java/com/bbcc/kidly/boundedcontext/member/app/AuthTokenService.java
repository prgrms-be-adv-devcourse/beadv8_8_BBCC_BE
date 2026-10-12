package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.security.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원의 역할을 계산해 토큰을 발급한다. 판매자 여부는 저장하지 않으므로 발급할 때마다 계산한다.
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

    // 판매자(SELLER)는 Seller 애그리거트가 생기면 추가한다
    private List<Role> rolesOf(Member member) {
        return member.isAdmin() ? List.of(Role.BUYER, Role.ADMIN) : List.of(Role.BUYER);
    }
}
