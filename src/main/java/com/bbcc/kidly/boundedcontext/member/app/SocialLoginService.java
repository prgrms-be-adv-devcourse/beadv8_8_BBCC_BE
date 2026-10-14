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
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.concurrent.ThreadLocalRandom;
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

    private static final String DEFAULT_NICKNAME_PREFIX = "회원";
    private static final int NUMBER_LENGTH = 4;
    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;
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
            return memberRegistrar.register(
                new MemberLoginRequest(provider, profile.providerUserId(), nicknameOf(profile)));
        } catch (DataIntegrityViolationException e) {
            // 닉네임이 겹쳐 실패하면 소셜 계정으로 다시 찾지 못하므로 그대로 던진다 (409)
            return memberRepository.findBySocialAccount(provider, profile.providerUserId())
                .orElseThrow(() -> e);
        }
    }

    // 닉네임은 사용자가 제공에 동의해야 온다. 없거나 2자 미만이면 "회원" + 숫자 4자리로 가입하고, 나중에 회원이 바꾼다
    // 20자를 넘으면 자르고, 다른 회원이 쓰고 있으면 숫자 4자리를 붙인다
    private String nicknameOf(OAuthProfile profile) {
        var nickname = profile.nickname() == null ? "" : cut(profile.nickname().strip(), Member.NICKNAME_MAX_LENGTH);
        if (nickname.length() < Member.NICKNAME_MIN_LENGTH) {
            return withNumber(DEFAULT_NICKNAME_PREFIX);
        }
        return memberRepository.existsByNickname(nickname) ? withNumber(nickname) : nickname;
    }

    // 앞에서 maxLength자까지 자른다. 이모지(서로게이트 쌍)를 반으로 가르지 않고, 잘린 끝의 공백은 뺀다
    // (도메인은 앞뒤 공백이 있는 닉네임을 거부한다)
    private static String cut(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        var end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end).strip();
    }

    // 숫자를 붙여도 20자를 넘지 않게 앞부분만 쓴다. 붙인 값도 겹치면 다시 뽑는다
    // 끝내 겹치거나 동시에 같은 값으로 가입하면 유니크 제약이 막는다 (409, 다시 로그인하면 된다)
    private String withNumber(String base) {
        var prefix = cut(base, Member.NICKNAME_MAX_LENGTH - NUMBER_LENGTH);
        var candidate = prefix + randomNumber();
        for (int attempt = 1;
             attempt < MAX_NUMBER_ATTEMPTS && memberRepository.existsByNickname(candidate);
             attempt++) {
            candidate = prefix + randomNumber();
        }
        return candidate;
    }

    private static int randomNumber() {
        return ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
