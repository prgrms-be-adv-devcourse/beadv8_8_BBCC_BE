package com.bbcc.kidly.shared.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gateway가 토큰에서 만들어 서비스로 넘기는 주체 헤더. 서비스는 이 헤더를 그대로 믿는다.
 * nginx 템플릿은 접두어로 지울 수 없어 {@link #ALL}을 이름으로 적는다(NginxPrincipalHeadersTest가 맞춘다).
 */
public final class PrincipalHeaders {

    public static final String CHILD_ACCOUNT_ID = "X-Child-Account-Id";
    public static final String CHILD_GUARDIAN_ID = "X-Child-Guardian-Id";
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
            case CHILD -> List.of(CHILD_ACCOUNT_ID, CHILD_GUARDIAN_ID);
        };
    }

    public static String idHeader(PrincipalType type) {
        return switch (type) {
            case MEMBER -> MemberHeaders.MEMBER_ID;
            case ADMIN -> ADMIN_ID;
            case CHILD -> CHILD_ACCOUNT_ID;
        };
    }

    // 없거나, 0-9로만 된 Long 범위의 수가 아니면(부호 · 공백 포함) 빈 값이다
    public static Optional<Long> parseId(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            // Long 범위를 넘는다
            return Optional.empty();
        }
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
            case CHILD -> "x-child-";
        };
    }
}
