package com.bbcc.kidly.shared.auth;

import java.util.Arrays;
import java.util.Objects;

/**
 * 로그인 주체 하나. Access Token의 sub 형식을 이 클래스만 안다.
 * 회원은 숫자 그대로("42"), 다른 주체는 접두어를 붙인다("admin:7").
 */
public record PrincipalId(PrincipalType type, long id) {

    public PrincipalId {
        Objects.requireNonNull(type);
        if (id <= 0) {
            throw new IllegalArgumentException("주체 ID는 양수여야 합니다");
        }
    }

    public static PrincipalId member(long id) {
        return new PrincipalId(PrincipalType.MEMBER, id);
    }

    public static PrincipalId admin(long id) {
        return new PrincipalId(PrincipalType.ADMIN, id);
    }

    public static PrincipalId child(long id) {
        return new PrincipalId(PrincipalType.CHILD, id);
    }

    public String toSubject() {
        return subjectPrefix(type) + id;
    }

    /**
     * @throws IllegalArgumentException 형식이 아니면
     */
    public static PrincipalId parseSubject(String subject) {
        var value = Objects.requireNonNullElse(subject, "");
        var type = Arrays.stream(PrincipalType.values())
            .filter(candidate -> !subjectPrefix(candidate).isEmpty() && value.startsWith(subjectPrefix(candidate)))
            .findFirst()
            .orElse(PrincipalType.MEMBER);
        var id = MemberHeaders.parseMemberId(value.substring(subjectPrefix(type).length()))
            .orElseThrow(() -> new IllegalArgumentException("잘못된 토큰 주체입니다"));
        return new PrincipalId(type, id);
    }

    private static String subjectPrefix(PrincipalType type) {
        return switch (type) {
            case MEMBER -> "";
            case ADMIN -> "admin:";
            case CHILD -> "child:";
        };
    }
}
