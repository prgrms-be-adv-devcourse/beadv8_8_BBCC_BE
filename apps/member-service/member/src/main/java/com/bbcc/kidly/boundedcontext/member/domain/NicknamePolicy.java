package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final MemberRepository memberRepository;

    public Nickname generate(String providerNickname) {
        return Nickname.fromProvider(providerNickname)
            .map(nickname -> isUsed(nickname) ? withNumber(nickname) : nickname)
            .orElseGet(() -> withNumber(Nickname.DEFAULT_BASE));
    }

    public boolean changeNickname(Member member, Nickname nickname) {
        if (memberRepository.existsByNicknameAndIdNot(nickname.value(), member.getId())) {
            throw new BusinessException(MemberErrorCode.NICKNAME_ALREADY_USED);
        }
        return member.changeNickname(nickname);
    }

    private boolean isUsed(Nickname nickname) {
        return memberRepository.existsByNickname(nickname.value());
    }

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
