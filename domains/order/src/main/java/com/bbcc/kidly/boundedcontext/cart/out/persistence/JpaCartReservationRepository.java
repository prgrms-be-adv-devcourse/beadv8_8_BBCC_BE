package com.bbcc.kidly.boundedcontext.cart.out.persistence;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartReservationRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartReservation;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 칸 예약 허용 주체 저장소를 Spring Data JPA로 구현한다(#998).
 */
public interface JpaCartReservationRepository extends Repository<CartReservation, Long>, CartReservationRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CartReservation r where r.reservationId = :reservationId order by r.id")
    List<CartReservation> lockAllByReservationId(String reservationId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CartReservation r where r.reservationId in :reservationIds order by r.reservationId, r.id")
    List<CartReservation> lockAllByReservationIdIn(Collection<String> reservationIds);
}
