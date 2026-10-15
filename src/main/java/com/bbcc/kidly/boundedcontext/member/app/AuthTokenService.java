package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 역할을 담아 토큰을 발급한다. 역할은 {@link Member#roles()}로 계산한다. SELLER는 저장하지 않으므로 발급할 때마다 판매자 상태 복사본으로 정해진다.
 * 발급할 때마다 관리자 이메일 목록({@link AdminProperties})을 회원 역할에 반영한다. 목록에서 빠진 관리자는 다음 로그인 · 재발급부터 ADMIN이 없다.
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final MemberRepository memberRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AdminProperties adminProperties;

    @Transactional
    public String issueAccessToken(Long memberId) {
        var member = memberRepository.getById(memberId);
        member.changeAdmin(adminProperties.isAdmin(member.getEmail()));
        return accessTokenIssuer.issue(memberId, member.getNickname(), member.roles());
    }
}
