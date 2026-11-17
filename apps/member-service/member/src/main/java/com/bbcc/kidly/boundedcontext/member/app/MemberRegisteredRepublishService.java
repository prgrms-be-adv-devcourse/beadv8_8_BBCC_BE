package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRegisteredRepublishService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int republishAll() {
        var members = memberRepository.findByEmailIsNotNull();
        for (var member : members) {
            eventPublisher.publishEvent(new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
                new MemberRegisteredEvent.Data(member.getId(), member.getEmail(), true)));
        }
        log.info("회원 가입 이벤트 다시 발행: publishedCount={}", members.size());
        return members.size();
    }
}
