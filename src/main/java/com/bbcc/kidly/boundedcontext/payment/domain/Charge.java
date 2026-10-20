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
import jakarta.persistence.Index;
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
 * 결제창이 끝나지 않으면 PENDING에서 바로 FAILED가 된다.
 * 토스에서 직접 취소된 충전은 DONE에서 바로 CANCELLED가 된다.
 * 상태를 바꿀 때는 리포지토리에서 비관적 락(SELECT ... FOR UPDATE)으로 읽어, 사용자 승인과 정리 스케줄러가 겹쳐도 순서대로 처리한다.
 */
@Entity
@Table(schema = "payment", name = "charge", indexes = @Index(columnList = "wallet_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Charge extends BaseTimeEntity {

    // 충전 · 충전 취소의 실패 코드 컬럼 길이
    static final int FAILURE_CODE_LENGTH = 100;


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

    // PG 승인 실패 시 토스 오류 코드. FAILURE_CODE_LENGTH자를 넘으면 잘라 남긴다
    @Column(length = FAILURE_CODE_LENGTH)
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
        this.failureCode = limitFailureCode(failureCode);
        this.status = ChargeStatus.FAILED;
    }

    /**
     * 결제창이 실패 · 취소로 끝나 승인 요청이 오지 않을 충전을 실패로 끝낸다. 이미 실패했으면 그대로 둔다.
     */
    public void abandon(String failureCode) {
        if (status == ChargeStatus.FAILED) {
            return;
        }
        requireStatus(ChargeStatus.PENDING);
        this.failureCode = limitFailureCode(failureCode);
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
        getLastCancellation().complete(cancelledAt);
        this.status = ChargeStatus.CANCELLED;
    }

    public void failCancel(String failureCode) {
        requireStatus(ChargeStatus.CANCELLING);
        getLastCancellation().fail(failureCode);
        this.status = ChargeStatus.DONE;
    }

    /**
     * 토스 상점관리자에서 직접 전액 취소된 충전을 맞춘다. 토스에서는 이미 끝났으므로 완료된 취소 요청을 남긴다.
     * 지갑에서 충전액을 빼는 일은 서비스가 한다.
     */
    public void cancelExternally(String reason, LocalDateTime cancelledAt) {
        requireStatus(ChargeStatus.DONE);
        ChargeCancellation cancellation = ChargeCancellation.request(this, amount, reason);
        cancellation.complete(cancelledAt);
        cancellations.add(cancellation);
        this.status = ChargeStatus.CANCELLED;
    }

    public List<ChargeCancellation> getCancellations() {
        return Collections.unmodifiableList(cancellations);
    }

    public ChargeCancellation getLastCancellation() {
        return cancellations.getLast();
    }

    private void requireStatus(ChargeStatus expected) {
        if (status != expected) {
            throw new BusinessException(PaymentErrorCode.INVALID_CHARGE_STATUS);
        }
    }

    static String limitFailureCode(String failureCode) {
        return failureCode != null && failureCode.length() > FAILURE_CODE_LENGTH
            ? failureCode.substring(0, FAILURE_CODE_LENGTH)
            : failureCode;
    }
}
