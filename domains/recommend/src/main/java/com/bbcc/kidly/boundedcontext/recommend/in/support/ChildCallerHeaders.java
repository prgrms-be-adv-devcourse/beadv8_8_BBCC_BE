package com.bbcc.kidly.boundedcontext.recommend.in.support;

import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;

/**
 * 같은 주소의 자녀 토큰용 메서드를 고르는 헤더 조건이다. {@code @GetMapping(headers = ...)}에 넣는다.
 * 자녀 계정 헤더가 있고 회원 헤더가 없을 때만 자녀 메서드로 가므로, 회원 요청은 예전 메서드가 그대로 받는다.
 */
public final class ChildCallerHeaders {

    public static final String CHILD_ACCOUNT = PrincipalHeaders.CHILD_ACCOUNT_ID;
    public static final String NOT_MEMBER = "!" + MemberHeaders.MEMBER_ID;

    private ChildCallerHeaders() {
    }
}
