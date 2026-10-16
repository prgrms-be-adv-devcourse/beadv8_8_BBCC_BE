package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 닉네임이 회원끼리 겹치지 않게 하는 도메인 서비스.
 * 가입할 때 쓸 닉네임을 정한다. 제공자 닉네임을 {@link Nickname} 규칙에 맞추고,
 * 다른 회원이 쓰고 있으면 숫자 4자리를 붙인다.
 * 닉네임을 바꿀 때는 다른 회원이 쓰는지 확인한다. 저장 · 이벤트 · 트랜잭션은 두지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final MemberRepository memberRepository;

    // 쓸 만한 제공자 닉네임이 없으면 Nickname.DEFAULT_BASE에 숫자 4자리를 붙인다 (회원이 나중에 바꾼다)
    // 다른 회원이 쓰고 있으면 숫자 4자리를 붙인다
    public Nickname generate(String providerNickname) {
        return Nickname.fromProvider(providerNickname)
            .map(nickname -> isUsed(nickname) ? withNumber(nickname) : nickname)
            .orElseGet(() -> withNumber(Nickname.DEFAULT_BASE));
    }

    // 다른 회원이 쓰는 닉네임이면 409. 바뀌면 true, 지금 닉네임과 같으면 false (형식 규칙은 Nickname이 지킨다)
    public boolean changeNickname(Member member, Nickname nickname) {
        if (memberRepository.existsByNicknameAndIdNot(nickname.value(), member.getId())) {
            throw new BusinessException(MemberErrorCode.NICKNAME_ALREADY_USED);
        }
        return member.changeNickname(nickname);
    }

    private boolean isUsed(Nickname nickname) {
        return memberRepository.existsByNickname(nickname.value());
    }

    // 붙인 값도 겹치면 다시 뽑는다
    // 끝내 겹치거나 동시에 같은 값으로 가입하면 유니크 제약이 막는다 (409, 다시 로그인하면 된다)
    private Nickname withNumber(Nickname base) {
        var candidate = base.withNumber(randomNumber());
        for (int attempt = 1; attempt < MAX_NUMBER_ATTEMPTS && isUsed(candidate); attempt++) {
            candidate = base.withNumber(randomNumber());
        }
        return candidate;
    }

    private static int randomNumber() {
        return ThreadLocalRandom.current().nextInt(Nickname.MIN_NUMBER, Nickname.MAX_NUMBER + 1);
    }
}
