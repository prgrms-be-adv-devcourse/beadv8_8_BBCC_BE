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

@Profile({"local", "test", "dev"})
@Service
@RequiredArgsConstructor
public class DevMemberSeedService {

    private static final String KEY_PREFIX = "seed-bulk:";

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;

    public DevMemberSeedResult seed(DevMemberSeedRequest request) {
        var registrationKey = KEY_PREFIX + request.key();
        var existing = memberRepository.findByRegistrationKey(registrationKey);
        if (existing.isPresent()) {
            return new DevMemberSeedResult(existing.get().getId(), false);
        }
        var info = new MemberRegisterInfo(registrationKey, new Nickname(request.nickname()), null);
        try {
            return new DevMemberSeedResult(memberRegistrar.register(info).getId(), true);
        } catch (DataIntegrityViolationException e) {
            var member = memberRepository.findByRegistrationKey(registrationKey).orElseThrow(() -> e);
            return new DevMemberSeedResult(member.getId(), false);
        }
    }
}
