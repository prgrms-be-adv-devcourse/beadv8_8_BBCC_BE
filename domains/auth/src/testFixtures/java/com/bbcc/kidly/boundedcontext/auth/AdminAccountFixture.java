package com.bbcc.kidly.boundedcontext.auth;

import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

public final class AdminAccountFixture {

    public static final String EMAIL = "admin@kidly.test";
    public static final String PASSWORD = "admin-password-1234";
    // PASSWORD의 BCrypt 해시. 테스트마다 다시 계산하지 않게 한 번만 만든다
    public static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    public static final Long CREATED_BY = 1L;

    private AdminAccountFixture() {
    }

    public static AdminAccount active() {
        return AdminAccount.create(EMAIL, PASSWORD_HASH, CREATED_BY);
    }

    public static AdminAccount disabled() {
        var account = active();
        ReflectionTestUtils.setField(account, "status", AdminStatus.DISABLED);
        return account;
    }
}
