package com.bbcc.kidly.boundedcontext.cart.out.persistence;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartReservationRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartReservation;
import org.springframework.data.repository.Repository;

/**
 * 칸 예약 허용 주체 저장소를 Spring Data JPA로 구현한다(#998).
 */
public interface JpaCartReservationRepository extends Repository<CartReservation, Long>, CartReservationRepository {
}
