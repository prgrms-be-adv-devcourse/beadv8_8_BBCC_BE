package com.bbcc.kidly.boundedcontext.payment.out.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * paymentKey 암호화 설정. 운영 키는 환경 변수 PAYMENT_KEY_ENCRYPTION_KEY로만 받는다.
 *
 * @param key Base64로 인코딩한 32바이트 AES 키
 */
@ConfigurationProperties("payment.key-encryption")
public record PaymentKeyCipherProperties(String key) {
}
