package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.cart.CartItem;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 줄 저장소. 구현은 out/persistence (Spring Data가 만든다).
 */
public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    Optional<CartItem> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 같은 줄(회원, 옵션, 자녀)을 찾는다. childId가 null이면 자녀를 지정하지 않은 줄을 찾는다.
     */
    Optional<CartItem> findByMemberIdAndOptionIdAndChildId(Long memberId, Long optionId, Long childId);

    /**
     * 최근에 담은 줄부터 돌려준다.
     */
    List<CartItem> findAllByMemberIdOrderByIdDesc(Long memberId);

    void delete(CartItem cartItem);
}
