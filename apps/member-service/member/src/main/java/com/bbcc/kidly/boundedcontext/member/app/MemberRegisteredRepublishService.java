package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일이 있는 회원마다 MemberRegisteredEvent를 resent=true로 다시 보낸다. 회원은 바꾸지 않는다.
 * 가입 이벤트를 놓친 쪽(나중에 붙은 알림의 이메일 복사본 등)이 따라잡게 한다.
 * 받는 쪽은 resent면 가입 때만 할 일(환영 메일)을 하지 않고, 복사본은 회원마다 한 건이라 여러 번 불러도 결과가 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRegisteredRepublishService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Kafka로는 이 트랜잭션이 커밋된 뒤 나간다(MemberEventRelay)
    @Transactional
    public int republishAll() {
        int publishedCount = 0;
        for (Member member : memberRepository.findByEmailIsNotNull()) {
            eventPublisher.publishEvent(new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
                new MemberRegisteredEvent.Data(member.getId(), member.getEmail(), true)));
            publishedCount++;
        }
        log.info("회원 가입 이벤트 다시 발행: publishedCount={}", publishedCount);
        return publishedCount;
    }
}
