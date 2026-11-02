package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.SocialLoginService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberWithdrawnEventListener {

    private final SocialLoginService socialLoginService;

    @DomainEventListener
    public void on(MemberWithdrawnEvent event) {
        socialLoginService.unlinkAll(event.data().memberId());
    }
}
