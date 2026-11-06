package com.bbcc.kidly.boundedcontext.cart.domain;

import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 칸 예약의 결제 허용 주체 한 명(#998). 예약 ID는 예약한 쪽이 준 불투명 문자열이고, 장바구니는 예약한 쪽이 누구인지 모른다.
 * 그 예약 칸은 허용 주체만 주문할 수 있다.
 */
@Entity
@Table(schema = "orders", name = "cart_reservation",
    uniqueConstraints = @UniqueConstraint(name = "uk_cart_reservation_payer",
        columnNames = {"reservation_id", "payer_type", "payer_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartReservation extends CartTimeEntity {

    public static final int RESERVATION_ID_MAX = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = RESERVATION_ID_MAX)
    private String reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnerType payerType;

    @Column(nullable = false)
    private Long payerId;

    public static CartReservation allow(String reservationId, OwnerType payerType, Long payerId) {
        CartReservation reservation = new CartReservation();
        reservation.reservationId = reservationId;
        reservation.payerType = payerType;
        reservation.payerId = payerId;
        return reservation;
    }

    public boolean allows(OwnerType type, Long id) {
        return payerType == type && payerId.equals(id);
    }
}
