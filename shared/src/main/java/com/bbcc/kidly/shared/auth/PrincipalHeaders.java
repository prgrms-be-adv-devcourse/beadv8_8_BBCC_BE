package com.bbcc.kidly.shared.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Gateway가 토큰에서 만들어 서비스로 넘기는 주체 헤더. 서비스는 이 헤더를 그대로 믿는다.
 * nginx 템플릿은 접두어로 지울 수 없어 {@link #ALL}을 이름으로 적는다(NginxPrincipalHeadersTest가 맞춘다).
 */
public final class PrincipalHeaders {

    public static final String ADMIN_ID = "X-Admin-Id";
    public static final List<String> ALL = Arrays.stream(PrincipalType.values())
        .flatMap(type -> namesOf(type).stream())
        .toList();

    private PrincipalHeaders() {
    }

    public static List<String> namesOf(PrincipalType type) {
        return switch (type) {
            case MEMBER -> List.of(MemberHeaders.MEMBER_ID, MemberHeaders.MEMBER_ROLES, MemberHeaders.MEMBER_NICKNAME);
            case ADMIN -> List.of(ADMIN_ID);
        };
    }

    public static boolean isPrincipalHeader(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(PrincipalType.values())
            .anyMatch(type -> lower.startsWith(prefixOf(type)));
    }

    private static String prefixOf(PrincipalType type) {
        return switch (type) {
            case MEMBER -> "x-member-";
            case ADMIN -> "x-admin-";
        };
    }
}
