package com.bbcc.kidly.boundedcontext.payment.domain;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "잘못된 금액입니다", "금액은 0보다 커야 합니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "예치금이 부족합니다", "예치금을 충전한 뒤 다시 시도해 주세요."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다", "회원의 예치금 지갑이 없습니다."),
    CHARGE_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "충전할 수 없는 금액입니다", "1천 원부터 100만 원까지 충전할 수 있습니다."),
    CHARGE_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "충전 금액이 다릅니다", "요청한 충전 금액과 결제 금액이 다릅니다."),
    CHARGE_EXPIRED(HttpStatus.CONFLICT, "만료된 충전입니다", "충전 요청이 만료됐습니다. 다시 요청해 주세요."),
    CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND, "충전을 찾을 수 없습니다", "해당 충전 요청이 없습니다."),
    CHARGE_REJECTED(HttpStatus.BAD_REQUEST, "충전 승인이 거절됐습니다", "결제가 승인되지 않았습니다. 다시 시도해 주세요."),
    INVALID_CHARGE_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 충전 상태입니다", "현재 충전 상태에서는 요청을 처리할 수 없습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 결제 상태입니다", "현재 결제 상태에서는 요청을 처리할 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다", "해당 결제가 없습니다."),
    PAYMENT_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 항목을 찾을 수 없습니다", "결제에 해당 주문상품이 없습니다."),
    REFUND_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "환불 가능 금액을 넘었습니다", "결제한 금액보다 많이 환불할 수 없습니다.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
