package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.NicknamePolicy;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.member.dto.MemberRegistration;
import com.bbcc.kidly.shared.member.out.MemberAccounts;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAccountsService implements MemberAccounts {

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;
    private final NicknamePolicy nicknamePolicy;

    @Override
    public MemberLoginState register(MemberRegistration registration) {
        var member = memberRepository.findByRegistrationKey(registration.registrationKey())
            .orElseGet(() -> insert(registration));
        return member.loginState();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberLoginState> findLoginState(Long memberId) {
        return memberRepository.findById(memberId).map(Member::loginState);
    }

    private Member insert(MemberRegistration registration) {
        var nickname = nicknamePolicy.generate(registration.nicknameHint());
        var info = new MemberRegisterInfo(registration.registrationKey(), nickname, registration.email());
        try {
            var member = memberRegistrar.register(info);
            log.info("회원 가입: memberId={}", member.getId());
            return member;
        } catch (DataIntegrityViolationException e) {
            var member = memberRepository.findByRegistrationKey(registration.registrationKey()).orElseThrow(() -> e);
            log.warn("동시 가입에서 진 요청을 기존 회원으로 돌려줌: memberId={}", member.getId());
            return member;
        }
    }
}
