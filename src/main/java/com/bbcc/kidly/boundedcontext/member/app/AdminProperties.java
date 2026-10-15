package com.bbcc.kidly.boundedcontext.member.app;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 관리자 지정. 이 목록의 이메일을 가진 회원은 토큰을 받을 때 ADMIN이 되고, 목록에서 빠지면 USER로 돌아간다.
 * 회원 이메일은 카카오에서 인증된 것만 저장하므로 본인 확인된 주소다.
 *
 * @param emails 관리자 이메일. 환경 변수 ADMIN_EMAILS에 쉼표로 나눠 넣는다. 비어 있으면 관리자가 없다
 */
@ConfigurationProperties("admin")
public record AdminProperties(@DefaultValue Set<String> emails) {

    public AdminProperties {
        emails = emails.stream()
            .map(AdminProperties::normalize)
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return email != null && emails.contains(normalize(email));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
