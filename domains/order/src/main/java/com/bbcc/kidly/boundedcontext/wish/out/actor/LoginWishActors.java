package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기존 로그인(회원)을 조르기 호출 주체로 바꾼다(#640). 지금 로그인은 부모 회원뿐이다.
 */
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class LoginWishActors implements WishActors {

    @Override
    public WishActor parent(Long memberId) {
        return new ParentWishActor(memberId);
    }
}
