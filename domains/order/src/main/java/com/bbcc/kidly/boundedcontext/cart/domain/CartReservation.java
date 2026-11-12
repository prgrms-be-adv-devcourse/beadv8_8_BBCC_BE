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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 칸 예약의 결제 허용 주체 한 명(#998). 예약 ID는 예약한 쪽이 준 불투명 문자열이고, 장바구니는 예약한 쪽이 누구인지 모른다.
 * 그 예약 칸은 허용 주체만 주문할 수 있다. 결제 가능 기한(validUntil, #1008)이 있으면 그 시각과 같거나 지난 뒤에는 허용 주체도
 * 주문할 수 없다. 기한은 예약한 쪽이 정하고(장바구니는 누가 왜 정했는지 모른다) 같은 예약의 모든 행이 같은 값을 갖는다.
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

    /**
     * 결제 가능 기한(#1008). null이면 기한 없음.
     */
    private LocalDateTime validUntil;

    /**
     * 결제 정리가 이 예약의 칸을 처음 정리한 시각(#999). 아직 없으면 null.
     */
    private LocalDateTime purchasedAt;

    /**
     * 낙관적 잠금 버전(#999). 결제 정리 표시 · 예약 해제 · 기한 변경은 이 행을 먼저 쓰기 잠금으로 잡고(#1207, 예약 → 예약한 쪽 → 칸 순서)
     * 줄을 서므로 보통은 이 버전으로 지는 일이 없다. 잠금 순서를 따르지 않는 경로가 생기면 마지막 안전장치로 남는다.
     */
    @Version
    private Long version;

    public static CartReservation allow(String reservationId, OwnerType payerType, Long payerId,
        LocalDateTime validUntil) {
        CartReservation reservation = new CartReservation();
        reservation.reservationId = reservationId;
        reservation.payerType = payerType;
        reservation.payerId = payerId;
        reservation.validUntil = validUntil;
        return reservation;
    }

    /**
     * 결제 정리 시각을 남긴다. 이미 있으면 그대로 둔다(처음 정리한 시각).
     */
    public void markPurchased(LocalDateTime at) {
        if (purchasedAt == null) {
            this.purchasedAt = at;
        }
    }

    public boolean isPurchased() {
        return purchasedAt != null;
    }

    // 기한 다시 맞추기는 이 행을 예약 잠금으로 먼저 잡고 바꾼다(#1207): 결제 정리 표시 · 예약 해제와 겹치면 잠금을 기다려 줄을 서고, 잠금
    // 실패(교착 등)면 동기화가 건별로 다시 맞춘다
    public void changeValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    /**
     * now가 기한과 같거나 지났는지. 기한이 없으면 false.
     */
    public boolean isExpiredAt(LocalDateTime now) {
        return validUntil != null && !now.isBefore(validUntil);
    }

    public boolean allows(OwnerType type, Long id) {
        return payerType == type && payerId.equals(id);
    }
}
