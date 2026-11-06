package com.bbcc.kidly.boundedcontext.wish.out.actor;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로그인을 조르기 호출 주체로 바꾼다: 회원 로그인은 부모(#640), 자녀 로그인은 그 자녀 프로필(#906).
 * 자녀 계정 → 프로필은 주문이 받아 둔 계정 복사본(child-account.changed)으로 찾고, 없거나 · 삭제됐거나 · 토큰의 보호자와 다르면 빈 값이다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class LoginWishActors implements WishActors {

    private final WishCatalog wishCatalog;

    @Override
    public WishActor parent(Long memberId) {
        return new ParentWishActor(memberId);
    }

    @Override
    public Optional<WishActor> child(Long accountId, Long guardianId) {
        return wishCatalog.findChildAccount(accountId)
            .filter(account -> !account.deleted() && account.guardianId().equals(guardianId))
            .map(account -> new ChildWishActor(account.childProfileId(), account.guardianId()));
    }
}
