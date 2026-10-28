package com.bbcc.kidly.boundedcontext.payment.out.crypto;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentKeyCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM. 저장 값은 Base64(IV 12바이트 + 암호문 + 인증 태그 16바이트)이고, IV는 암호화할 때마다 새로 만든다.
 * 키나 저장 값이 잘못되면 IllegalStateException이다.
 */
public class AesGcmPaymentKeyCipher implements PaymentKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmPaymentKeyCipher(String base64Key) {
        byte[] keyBytes = decodeKey(base64Key);
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("paymentKey 암호화 키는 Base64로 인코딩한 32바이트여야 합니다");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String paymentKey) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(paymentKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("paymentKey를 암호화하지 못했습니다", e);
        }
    }

    @Override
    public String decrypt(String encrypted) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("paymentKey를 복호화하지 못했습니다", e);
        }
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("paymentKey 암호화 키(PAYMENT_KEY_ENCRYPTION_KEY)가 없습니다");
        }
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("paymentKey 암호화 키가 Base64 형식이 아닙니다", e);
        }
    }
}
