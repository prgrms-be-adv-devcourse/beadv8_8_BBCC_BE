package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.LoggedInMember;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.OAuthClient;
import com.bbcc.kidly.boundedcontext.member.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.NicknameGenerator;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 소셜 계정으로 로그인한다. 처음이면 가입시킨다. 토큰은 {@link AuthTokenService}가 발급한다.
 * 트랜잭션을 걸지 않는다. 동시 가입으로 가입 트랜잭션이 롤백돼도 다시 조회할 수 있어야 하기 때문이다.
 * 소셜 제공자 호출도 트랜잭션 밖이라서 제공자가 느려도 DB 커넥션을 붙잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;
    private final NicknameGenerator nicknameGenerator;
    private final OAuthClient oauthClient;

    // provider는 URL 경로의 소문자 이름(kakao)이다
    public LoggedInMember login(String providerName, SocialLoginRequest request) {
        var provider = OAuthProvider.from(providerName);
        var profile = oauthClient.fetchProfile(provider, request.code());
        var member = memberRepository.findBySocialAccount(provider, profile.providerUserId())
            .orElseGet(() -> register(provider, profile));
        if (!member.canLogin()) {
            throw new BusinessException(MemberErrorCode.LOGIN_NOT_ALLOWED);
        }
        return new LoggedInMember(member.getId());
    }

    // 같은 소셜 계정으로 동시에 처음 로그인하면 한 요청만 가입되고, 나머지는 그 회원으로 로그인한다
    private Member register(OAuthProvider provider, OAuthProfile profile) {
        try {
            var nickname = nicknameGenerator.generate(profile.nickname());
            return memberRegistrar.register(new MemberLoginRequest(provider, profile.providerUserId(), nickname));
        } catch (DataIntegrityViolationException e) {
            // 닉네임이 겹쳐 실패하면 소셜 계정으로 다시 찾지 못하므로 그대로 던진다 (409)
            return memberRepository.findBySocialAccount(provider, profile.providerUserId())
                .orElseThrow(() -> e);
        }
    }
}
