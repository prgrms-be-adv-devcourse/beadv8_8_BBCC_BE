package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 닉네임이 회원끼리 겹치지 않게 하는 도메인 서비스.
 * 가입할 때 쓸 닉네임을 정한다. 제공자 닉네임을 정리해 회원 닉네임 규칙(2~20자, 앞뒤 공백 없음)에 맞추고,
 * 다른 회원이 쓰고 있으면 숫자 4자리를 붙인다.
 * 닉네임을 바꿀 때는 다른 회원이 쓰는지 확인한다. 저장 · 이벤트 · 트랜잭션은 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    private static final String DEFAULT_NICKNAME_PREFIX = "회원";
    private static final int NUMBER_LENGTH = 4;
    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final MemberRepository memberRepository;

    // 닉네임은 사용자가 제공에 동의해야 온다. 없거나 2자 미만이면 "회원" + 숫자 4자리로 가입하고, 나중에 회원이 바꾼다
    // 20자를 넘으면 자르고, 다른 회원이 쓰고 있으면 숫자 4자리를 붙인다
    public String generate(String providerNickname) {
        var nickname = providerNickname == null ? "" : cut(providerNickname.strip(), Member.NICKNAME_MAX_LENGTH);
        if (nickname.length() < Member.NICKNAME_MIN_LENGTH) {
            return withNumber(DEFAULT_NICKNAME_PREFIX);
        }
        return memberRepository.existsByNickname(nickname) ? withNumber(nickname) : nickname;
    }

    // 다른 회원이 쓰는 닉네임이면 409. 바뀌면 true, 지금 닉네임과 같으면 false (형식 규칙은 Member가 지킨다)
    public boolean changeNickname(Member member, String nickname) {
        if (memberRepository.existsByNicknameAndIdNot(nickname, member.getId())) {
            throw new BusinessException(MemberErrorCode.NICKNAME_ALREADY_USED);
        }
        return member.changeNickname(nickname);
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
