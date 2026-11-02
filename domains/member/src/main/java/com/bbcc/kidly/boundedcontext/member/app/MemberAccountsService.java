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

/**
 * 클래스에 트랜잭션을 걸지 않는다. 가입 트랜잭션이 유니크 충돌로 롤백돼도 가입 키로 다시 찾을 수 있어야 하기 때문이다.
 */
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

    // 같은 키 동시 가입은 키 유니크보다 닉네임 유니크가 먼저 걸릴 수 있어 제약 이름을 가리지 않고 키로 다시 찾는다
    // 키로 못 찾으면 다른 회원과 닉네임이 겹친 것이므로 그대로 던진다(409)
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
