package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.DevMemberSeedRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.DevMemberSeedResult;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 더미 회원을 가입시킨다. 카카오 가입 없이 회원 행을 만들 방법이 없어서 둔다. 운영에서는 등록하지 않는다.
 * MemberAccountsService처럼 클래스에 트랜잭션을 걸지 않는다. 가입 트랜잭션이 유니크 충돌로 롤백돼도 가입 키로 다시 찾아야 하기 때문이다.
 */
@Profile({"local", "test", "dev"})
@Service
@RequiredArgsConstructor
public class DevMemberSeedService {

    // 인증이 만드는 키(social-account:ID) · 초기 데이터 키(seed:번호)와 겹치지 않게 한다
    private static final String KEY_PREFIX = "seed-bulk:";

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;

    public DevMemberSeedResult seed(DevMemberSeedRequest request) {
        var registrationKey = KEY_PREFIX + request.key();
        var existing = memberRepository.findByRegistrationKey(registrationKey);
        if (existing.isPresent()) {
            return new DevMemberSeedResult(existing.get().getId(), false);
        }
        // 이메일이 없으면 알림이 환영 메일을 건너뛴다
        var info = new MemberRegisterInfo(registrationKey, new Nickname(request.nickname()), null);
        try {
            return new DevMemberSeedResult(memberRegistrar.register(info).getId(), true);
        } catch (DataIntegrityViolationException e) {
            // 키로 못 찾으면 다른 키의 회원과 닉네임이 겹친 것이므로 그대로 던진다(409)
            var member = memberRepository.findByRegistrationKey(registrationKey).orElseThrow(() -> e);
            return new DevMemberSeedResult(member.getId(), false);
        }
    }
}
