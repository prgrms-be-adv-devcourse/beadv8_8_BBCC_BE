package com.bbcc.kidly.shared.auth;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gateway가 토큰을 검증하고 서비스로 넘기는 회원 헤더. Gateway가 붙이고 서비스가 읽는다.
 * 닉네임은 헤더에 한글을 그대로 넣을 수 없어 UTF-8 퍼센트 인코딩(공백은 %20)으로 넣는다.
 */
public final class MemberHeaders {

    public static final String MEMBER_ID = "X-Member-Id";
    // 역할 이름 목록. 형식은 encodeRoles
    public static final String MEMBER_ROLES = "X-Member-Roles";
    public static final String MEMBER_NICKNAME = "X-Member-Nickname";
    private static final String ROLE_DELIMITER = ",";

    private MemberHeaders() {
    }

    // 역할 이름(roles 클레임 값, Role.name())을 쉼표로 잇는다(BUYER,SELLER). 역할이 없으면 빈 문자열
    public static String encodeRoles(Collection<String> roles) {
        return String.join(ROLE_DELIMITER, roles);
    }

    // null이나 빈 문자열이면 역할이 없다
    public static Set<String> decodeRoles(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(encoded.split(ROLE_DELIMITER)).collect(Collectors.toUnmodifiableSet());
    }

    // URLEncoder는 공백을 +로 만들므로 %20으로 바꾼다. 닉네임 안의 + 문자는 이미 %2B라 섞이지 않는다
    public static String encodeNickname(String nickname) {
        return URLEncoder.encode(nickname, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * @throws IllegalArgumentException 퍼센트 시퀀스가 잘못된 경우
     */
    public static String decodeNickname(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
