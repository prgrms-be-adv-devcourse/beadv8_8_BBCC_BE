package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 탈퇴하면 그 회원의 배송지를 모두 삭제한다.
 */
@Component
@RequiredArgsConstructor
public class ShippingAddressMemberEventListener {

    private final ShippingAddressService shippingAddressService;

    @DomainEventListener
    public void on(MemberWithdrawnEvent event) {
        shippingAddressService.deleteAll(event.data().memberId());
    }
}
