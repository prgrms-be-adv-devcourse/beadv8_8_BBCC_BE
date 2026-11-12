package com.bbcc.kidly.boundedcontext.cart.out.persistence;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 장바구니 줄 저장소를 Spring Data JPA로 구현한다. 도메인 인터페이스에 선언한 메서드만 노출한다.
 */
public interface JpaCartItemRepository extends Repository<CartItem, Long>, CartItemRepository {

    @Override
    @Query("select i.reservationId from CartItem i where i.id = :id")
    Optional<String> findReservationIdById(Long id);

    @Override
    @Query("select distinct i.reservationId from CartItem i where i.ownerType = :ownerType and i.ownerId = :ownerId "
        + "and i.reservationId is not null")
    List<String> findReservationIdsByOwner(OwnerType ownerType, Long ownerId);
}
