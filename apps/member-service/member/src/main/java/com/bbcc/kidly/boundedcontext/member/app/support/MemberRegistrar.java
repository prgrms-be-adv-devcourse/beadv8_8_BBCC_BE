package com.bbcc.kidly.boundedcontext.member.app.support;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberRegistrar {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Member register(MemberRegisterInfo info) {
        var now = LocalDateTime.now();

        var member = memberRepository.save(Member.register(info));

        eventPublisher.publishEvent(new MemberRegisteredEvent(
            UUID.randomUUID(),
            now,
            new MemberRegisteredEvent.Data(member.getId(), member.getEmail())
        ));

        return member;
    }
}
