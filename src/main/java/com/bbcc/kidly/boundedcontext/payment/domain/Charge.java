package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스 결제창으로 예치금을 충전하는 요청.
 * PENDING → CONFIRMING → DONE 순서로 승인되고, DONE에서 CANCELLING을 거쳐 CANCELLED로 취소된다.
 */
@Entity
@Table(schema = "payment", name = "charge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Charge extends BaseTimeEntity {

    static final long MIN_AMOUNT = 1_000L;
    static final long MAX_AMOUNT = 1_000_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    // 토스에 넘기는 주문번호
    @Column(nullable = false, unique = true, length = 64)
    private String tossOrderId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status;

    // 토스 paymentKey(최대 200자)를 AES-GCM으로 암호화한 Base64 값
    @Column(length = 512)
    private String paymentKeyEnc;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime approvedAt;

    // PG 승인 실패 시 토스 오류 코드
    @Column(length = 50)
    private String failureCode;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL)
    private List<ChargeCancellation> cancellations = new ArrayList<>();

    private Charge(Long walletId, String tossOrderId, long amount, LocalDateTime expiresAt) {
        this.walletId = walletId;
        this.tossOrderId = tossOrderId;
        this.amount = amount;
        this.status = ChargeStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public static Charge request(Long walletId, String tossOrderId, long amount, LocalDateTime expiresAt) {
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(PaymentErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE);
        }
        return new Charge(walletId, tossOrderId, amount, expiresAt);
    }

    /**
     * PG 승인을 호출하기 전에 결제창에서 돌아온 금액을 대조하고 승인 중으로 바꾼다.
     */
    public void startConfirming(long paidAmount, LocalDateTime now) {
        requireStatus(ChargeStatus.PENDING);
        if (now.isAfter(expiresAt)) {
            throw new BusinessException(PaymentErrorCode.CHARGE_EXPIRED);
        }
        if (paidAmount != amount) {
            throw new BusinessException(PaymentErrorCode.CHARGE_AMOUNT_MISMATCH);
        }
        this.status = ChargeStatus.CONFIRMING;
    }

    public void approve(String paymentKeyEnc, LocalDateTime approvedAt) {
        requireStatus(ChargeStatus.CONFIRMING);
        this.paymentKeyEnc = paymentKeyEnc;
        this.approvedAt = approvedAt;
        this.status = ChargeStatus.DONE;
    }

    public void fail(String failureCode) {
        requireStatus(ChargeStatus.CONFIRMING);
        this.failureCode = failureCode;
        this.status = ChargeStatus.FAILED;
    }

    public void expire() {
        requireStatus(ChargeStatus.PENDING);
        this.status = ChargeStatus.EXPIRED;
    }

    /**
     * 전액 취소를 요청한다. 지갑에서 충전액을 빼는 일은 서비스가 한다.
     */
    public void requestCancel(String reason) {
        requireStatus(ChargeStatus.DONE);
        cancellations.add(ChargeCancellation.request(this, amount, reason));
        this.status = ChargeStatus.CANCELLING;
    }

    public void completeCancel(LocalDateTime cancelledAt) {
        requireStatus(ChargeStatus.CANCELLING);
        lastCancellation().complete(cancelledAt);
        this.status = ChargeStatus.CANCELLED;
    }

    public void failCancel(String failureCode) {
        requireStatus(ChargeStatus.CANCELLING);
        lastCancellation().fail(failureCode);
        this.status = ChargeStatus.DONE;
    }

    public List<ChargeCancellation> getCancellations() {
        return Collections.unmodifiableList(cancellations);
    }

    private ChargeCancellation lastCancellation() {
        return cancellations.getLast();
    }

    private void requireStatus(ChargeStatus expected) {
        if (status != expected) {
            throw new BusinessException(PaymentErrorCode.INVALID_CHARGE_STATUS);
        }
    }
}
