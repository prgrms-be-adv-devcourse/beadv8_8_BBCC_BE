package com.bbcc.kidly.boundedcontext.payment.domain;

public enum ChargeStatus {
    PENDING,
    CONFIRMING,
    DONE,
    CANCELLING,
    CANCELLED,
    FAILED,
    EXPIRED
}
