package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.out.crypto.AesGcmPaymentKeyCipher;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmPaymentKeyCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(filled((byte) 1));
    private static final String PAYMENT_KEY = "tgen_20261014120000abcdefghij";

    @Test
    @DisplayName("암호화한 값을 복호화하면 원래 paymentKey다")
    void roundTrip() {
        AesGcmPaymentKeyCipher cipher = new AesGcmPaymentKeyCipher(KEY);

        String encrypted = cipher.encrypt(PAYMENT_KEY);

        assertThat(encrypted).isNotEqualTo(PAYMENT_KEY);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(PAYMENT_KEY);
    }

    @Test
    @DisplayName("같은 값도 암호화할 때마다 IV가 달라 결과가 다르다")
    void differentIvEachTime() {
        AesGcmPaymentKeyCipher cipher = new AesGcmPaymentKeyCipher(KEY);

        assertThat(cipher.encrypt(PAYMENT_KEY)).isNotEqualTo(cipher.encrypt(PAYMENT_KEY));
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void otherKeyCannotDecrypt() {
        String encrypted = new AesGcmPaymentKeyCipher(KEY).encrypt(PAYMENT_KEY);

        assertThatThrownBy(() -> new AesGcmPaymentKeyCipher(OTHER_KEY).decrypt(encrypted))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 비어 있거나 32바이트가 아니면 만들 수 없다")
    void requiresThirtyTwoByteKey() {
        assertThatThrownBy(() -> new AesGcmPaymentKeyCipher(""))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmPaymentKeyCipher(Base64.getEncoder().encodeToString(new byte[16])))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("토스 paymentKey 최대 길이(200자)를 암호화해도 컬럼 길이(512자) 안에 들어간다")
    void fitsColumn() {
        String encrypted = new AesGcmPaymentKeyCipher(KEY).encrypt("k".repeat(200));

        assertThat(encrypted.length()).isLessThanOrEqualTo(512);
    }

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
