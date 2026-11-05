package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CI 대신 쓰는 본인 식별값을 만든다. CI는 본인인증 제공사와 계약해야 받을 수 있어서 인증 결과의 이름 · 생년월일 · 휴대폰 번호로 만든다.
 * 같은 사람이 여러 번 인증해도 같은 값이 나와야 중복 인증을 막을 수 있다. 비밀키로 HMAC해서 값만으로 개인정보를 맞혀 볼 수 없다.
 * 비밀키를 바꾸면 기존 값과 비교할 수 없게 되므로 바꾸지 않는다
 */
@Component
public class IdentityKeyGenerator {
    private final String secret;

    public IdentityKeyGenerator(@Value("${kidly.childprofile.verification.identity-key-secret}") String secret) {
        this.secret = secret;
    }

    public String generate(VerifiedIdentity identity) {
        var name = Normalizer.normalize(identity.name().strip(), Normalizer.Form.NFC);
        var phone = identity.phoneNumber().replaceAll("[^0-9]", "");
        var input = name + "|" + identity.birthDate() + "|" + phone;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
