package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 탈퇴하면 그 회원의 자녀 프로필을 모두 삭제한다.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileMemberEventListener {

    private final ChildProfileService childProfileService;

    @DomainEventListener
    public void on(MemberWithdrawnEvent event) {
        childProfileService.deleteAll(event.data().memberId());
    }
}
