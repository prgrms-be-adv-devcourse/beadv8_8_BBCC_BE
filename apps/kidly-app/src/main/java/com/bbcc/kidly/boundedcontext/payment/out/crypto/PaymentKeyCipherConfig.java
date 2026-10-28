package com.bbcc.kidly.boundedcontext.payment.out.crypto;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 키가 없거나 잘못되면 앱이 뜨지 않는다. 암호화하지 못한 paymentKey를 저장하는 일이 없게 시작할 때 막는다.
 */
@Configuration
class PaymentKeyCipherConfig {

    @Bean
    PaymentKeyCipher paymentKeyCipher(PaymentKeyCipherProperties properties) {
        return new AesGcmPaymentKeyCipher(properties.key());
    }
}
