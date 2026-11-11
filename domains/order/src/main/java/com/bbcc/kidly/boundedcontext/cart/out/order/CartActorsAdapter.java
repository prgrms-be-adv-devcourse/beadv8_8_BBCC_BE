package com.bbcc.kidly.boundedcontext.cart.out.order;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 로그인을 장바구니 주인으로 바꾼다(#998): 회원은 그 회원, 자녀는 주문이 받아 둔 자녀 계정 복사본(#906)으로 찾은 프로필.
 * 복사본은 모듈 공용 기반(주문 컨텍스트의 복사본 조회 포트)으로 읽는다.
 */
@Component
@RequiredArgsConstructor
public class CartActorsAdapter implements CartActors {

    private final ReplicaLookup replicaLookup;

    @Override
    public Actor member(Long memberId) {
        return new Actor(OwnerType.MEMBER, memberId, memberId);
    }

    @Override
    public Optional<Actor> child(Long accountId, Long guardianId) {
        return replicaLookup.findChildAccount(accountId)
            .filter(account -> !account.deleted() && account.guardianId().equals(guardianId))
            .map(account -> new Actor(OwnerType.CHILD, account.childProfileId(), account.guardianId()));
    }
}
