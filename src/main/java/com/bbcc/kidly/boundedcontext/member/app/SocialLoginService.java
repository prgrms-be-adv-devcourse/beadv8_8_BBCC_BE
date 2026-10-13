package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.OAuthClient;
import com.bbcc.kidly.boundedcontext.member.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 소셜 계정으로 로그인한다. 처음이면 가입시킨다. 토큰은 {@link AuthTokenService}가 발급한다.
 * 트랜잭션을 걸지 않는다. 동시 가입으로 가입 트랜잭션이 롤백돼도 다시 조회할 수 있어야 하기 때문이다.
 * 소셜 제공자 호출도 트랜잭션 밖이라서 제공자가 느려도 DB 커넥션을 붙잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final String DEFAULT_NICKNAME_PREFIX = "회원";

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;
    private final OAuthClient oauthClient;

    public Member loginWithCode(OAuthProvider provider, SocialLoginRequest request) {
        var profile = oauthClient.fetchProfile(provider, request.code());
        return login(new MemberLoginRequest(provider, profile.providerUserId(), nicknameOf(profile)));
    }

    public Member login(MemberLoginRequest request) {
        var member = memberRepository.findBySocialAccount(request.provider(), request.providerUserId())
            .orElseGet(() -> register(request));
        if (!member.canLogin()) {
            throw new BusinessException(MemberErrorCode.LOGIN_NOT_ALLOWED);
        }
        return member;
    }

    // 같은 소셜 계정으로 동시에 처음 로그인하면 한 요청만 가입되고, 나머지는 그 회원으로 로그인한다
    private Member register(MemberLoginRequest request) {
        try {
            return memberRegistrar.register(request);
        } catch (DataIntegrityViolationException e) {
            return memberRepository.findBySocialAccount(request.provider(), request.providerUserId())
                .orElseThrow(() -> e);
        }
    }

    // 닉네임은 사용자가 제공에 동의해야 온다. 없으면 "회원" + 숫자 4자리로 가입하고, 나중에 회원이 바꾼다
    private String nicknameOf(OAuthProfile profile) {
        if (StringUtils.hasText(profile.nickname())) {
            return profile.nickname();
        }
        return DEFAULT_NICKNAME_PREFIX + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
