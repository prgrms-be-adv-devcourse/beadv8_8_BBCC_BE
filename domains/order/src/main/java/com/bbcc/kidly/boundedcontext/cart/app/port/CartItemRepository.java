package com.bbcc.kidly.boundedcontext.cart.app.port;

import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 줄 저장소. 구현은 out/persistence (Spring Data가 만든다). 줄은 주인(#998) 기준으로 찾는다.
 */
public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    Optional<CartItem> findById(Long id);

    /**
     * 칸의 예약 ID만 읽는다(칸 엔티티를 올리지 않는다, #1207). 예약 행을 먼저 잠근 뒤 칸을 읽으려고 쓴다. 칸이 없거나 예약이 없으면 빈 값.
     */
    Optional<String> findReservationIdById(Long id);

    List<CartItem> findAllByIdIn(Collection<Long> ids);

    /**
     * 같은 줄(주인, 옵션, 입을 자녀)을 찾는다. childId가 null이면 자녀를 지정하지 않은 줄을 찾는다.
     */
    Optional<CartItem> findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType ownerType, Long ownerId,
        Long optionId, Long childId);

    /**
     * 그 주인의 줄을 최근에 담은 것부터 돌려준다.
     */
    List<CartItem> findAllByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType ownerType, Long ownerId);

    /**
     * 그 주인 칸들의 예약 ID만 읽는다(칸 엔티티를 올리지 않는다, #1207). 칸을 읽기 전에 그 예약들을 잠그려고 쓴다. 예약 없는 칸은 빠진다.
     */
    List<String> findReservationIdsByOwner(OwnerType ownerType, Long ownerId);

    List<CartItem> findAllByReservationId(String reservationId);

    void delete(CartItem cartItem);
}
