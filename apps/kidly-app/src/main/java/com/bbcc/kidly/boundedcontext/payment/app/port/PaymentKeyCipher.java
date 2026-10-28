package com.bbcc.kidly.boundedcontext.payment.app.port;

/**
 * 토스 paymentKey를 DB에 저장하기 전에 암호화하고, 취소할 때 복호화한다.
 */
public interface PaymentKeyCipher {

    String encrypt(String paymentKey);

    String decrypt(String encrypted);
}
