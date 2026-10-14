package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberNicknameChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 닉네임을 바꾸면 그 회원의 판매자 신청에 반영한다.
 */
@Component
@RequiredArgsConstructor
public class MemberEventListener {

    private final SellerApplicationService sellerApplicationService;

    @DomainEventListener
    public void on(MemberNicknameChangedEvent event) {
        var data = event.data();
        sellerApplicationService.changeApplicantNickname(data.memberId(), data.nickname(), event.occurredAt());
    }
}
