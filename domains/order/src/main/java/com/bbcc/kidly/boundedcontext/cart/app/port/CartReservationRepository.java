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

    /**
     * 그 예약의 허용 주체 행을 id 순서로 읽으며 쓰기 잠금(SELECT … FOR UPDATE)을 잡는다(#1207). 잠금은 호출한 트랜잭션이 끝날 때까지
     * 유지한다. 예약 칸을 다루는 모든 경로가 이 잠금을 칸(cart_item)보다 먼저 잡아 같은 순서로 잠근다(교착 방지).
     */
    List<CartReservation> lockAllByReservationId(String reservationId);

    /**
     * 여러 예약의 허용 주체 행을 (예약 ID, id) 순서로 읽으며 쓰기 잠금을 잡는다(#1207). 여러 예약을 다루는 경로가 같은 전체 순서로 잠가
     * 서로 교착하지 않는다(예약 하나만 잠그는 {@link #lockAllByReservationId}도 이 순서의 일부다). 빈 목록이면 빈 목록.
     */
    List<CartReservation> lockAllByReservationIdIn(Collection<String> reservationIds);

    void delete(CartReservation reservation);
}
