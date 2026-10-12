package com.bbcc.kidly.boundedcontext.member.app.support;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 가입. 저장과 가입 이벤트 발행을 한 트랜잭션에서 한다.
 * 이미 연결된 소셜 계정이면 {@code DataIntegrityViolationException}을 던지고 이벤트는 발행하지 않는다.
 * 호출하는 쪽은 트랜잭션 밖에서 호출해야 이 예외를 잡고 다시 조회할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class MemberRegistrar {

    private final MemberRepository memberRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public Member register(MemberLoginRequest request) {
        var now = LocalDateTime.now();

        Member member = memberRepository.save(
            Member.register(request.provider(), request.providerUserId(), request.nickname(), now));

        eventPublisher.publish(new MemberRegisteredEvent(
            UUID.randomUUID(),
            now,
            new MemberRegisteredEvent.Data(member.getId())
        ));

        return member;
    }
}
