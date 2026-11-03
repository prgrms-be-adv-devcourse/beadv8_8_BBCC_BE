package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.app.AdminBootstrapService;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminStatus;
import com.bbcc.kidly.boundedcontext.auth.out.JpaAdminAccountRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class AdminBootstrapIntegrationTest {

    private static final String EMAIL = " Ops@Kidly.KR ";
    private static final String PASSWORD = "bootstrap-password-1";

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @Autowired
    private JpaAdminAccountRepository jpaAdminAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("관리자가 없으면 정규화한 이메일 · BCrypt 해시 · ACTIVE로 첫 관리자를 만든다. 만든 관리자 ID는 비어 있다")
    void createsFirstAdmin() {
        adminBootstrapService.bootstrap(EMAIL, PASSWORD);

        assertThat(jpaAdminAccountRepository.findAll()).singleElement().satisfies(admin -> {
            assertThat(admin.getEmail()).isEqualTo("ops@kidly.kr");
            assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
            assertThat(admin.getStatus()).isEqualTo(AdminStatus.ACTIVE);
            assertThat(admin.getCreatedBy()).isNull();
            assertThat(admin.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("두 번 돌아도 한 행이다")
    void runsTwiceLeavesOneRow() {
        adminBootstrapService.bootstrap(EMAIL, PASSWORD);
        adminBootstrapService.bootstrap(EMAIL, PASSWORD);

        assertThat(jpaAdminAccountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 이미 있으면 다른 이메일 · 비밀번호로 돌아도 아무것도 바꾸지 않는다")
    void ignoresWhenAdminExists() {
        adminBootstrapService.bootstrap(EMAIL, PASSWORD);

        adminBootstrapService.bootstrap("other@kidly.kr", "other-password-12");

        assertThat(jpaAdminAccountRepository.findAll()).singleElement().satisfies(admin -> {
            assertThat(admin.getEmail()).isEqualTo("ops@kidly.kr");
            assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        });
    }

    @Test
    @DisplayName("관리자가 만든 관리자만 있어도(첫 관리자 행이 없어도) 만들지 않는다")
    void ignoresWhenCreatedAdminExists() {
        jpaAdminAccountRepository.save(AdminAccountFixture.active());

        adminBootstrapService.bootstrap(EMAIL, PASSWORD);

        assertThat(jpaAdminAccountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 이메일로 동시에 돌아도 한 행이다")
    void concurrentBootstrapLeavesOneRow() throws Exception {
        var start = new CountDownLatch(1);
        var emails = List.of("first@kidly.kr", "second@kidly.kr");

        try (var executor = Executors.newFixedThreadPool(emails.size())) {
            var futures = emails.stream()
                .map(email -> executor.submit(() -> {
                    start.await();
                    adminBootstrapService.bootstrap(email, PASSWORD);
                    return email;
                }))
                .toList();
            start.countDown();
            for (Future<String> future : futures) {
                future.get();
            }
        }

        assertThat(jpaAdminAccountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일 설정이 비어 있으면 아무것도 하지 않는다")
    void skipsWithoutEmail() {
        adminBootstrapService.bootstrap("", PASSWORD);
        adminBootstrapService.bootstrap(null, null);

        assertThat(jpaAdminAccountRepository.count()).isZero();
    }

    @Test
    @DisplayName("이메일은 있는데 비밀번호가 비어 있으면 실패한다(기동 실패)")
    void failsWithoutPassword() {
        assertThatThrownBy(() -> adminBootstrapService.bootstrap(EMAIL, ""))
            .isInstanceOf(IllegalStateException.class);

        assertThat(jpaAdminAccountRepository.count()).isZero();
    }

    @Test
    @DisplayName("비밀번호가 12자보다 짧으면 실패한다")
    void failsWithShortPassword() {
        assertThatThrownBy(() -> adminBootstrapService.bootstrap(EMAIL, "short-pw"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(jpaAdminAccountRepository.count()).isZero();
    }
}
