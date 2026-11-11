package com.bbcc.kidly.boundedcontext.cart.app.port;

import com.bbcc.kidly.boundedcontext.cart.domain.CartReservation;
import java.util.Collection;
import java.util.List;

/**
 * 칸 예약의 결제 허용 주체 저장소(#998).
 */
public interface CartReservationRepository {

    CartReservation save(CartReservation reservation);

    List<CartReservation> findAllByReservationId(String reservationId);

    List<CartReservation> findAllByReservationIdIn(Collection<String> reservationIds);

    void delete(CartReservation reservation);
}
