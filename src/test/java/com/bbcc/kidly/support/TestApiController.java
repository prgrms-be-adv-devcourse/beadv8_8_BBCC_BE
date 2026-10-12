package com.bbcc.kidly.support;

import com.bbcc.kidly.global.auth.LoginMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기능 테스트에서만 쓰는 API. 테스트 클래스 안의 중첩 클래스는 TestTypeExcludeFilter가 스캔에서 빼므로 최상위 클래스로 둔다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합·기능 테스트 컨텍스트에 등록되므로 경로가 실제 API와 겹치지 않게 한다.
 */
@RestController
public class TestApiController {

    public static final String AUTH_PATH = "/api/v1/test/auth";
    public static final String SELLER_PATH = "/api/v1/seller/test";
    public static final String ADMIN_PATH = "/api/v1/admin/test";
    public static final String LOGIN_MEMBER_PATH = "/api/v1/test/login-member";

    @GetMapping(AUTH_PATH)
    void auth() {
    }

    @GetMapping(SELLER_PATH)
    void seller() {
    }

    @GetMapping(ADMIN_PATH)
    void admin() {
    }

    @GetMapping(LOGIN_MEMBER_PATH)
    Long loginMember(@LoginMember Long memberId) {
        return memberId;
    }
}
