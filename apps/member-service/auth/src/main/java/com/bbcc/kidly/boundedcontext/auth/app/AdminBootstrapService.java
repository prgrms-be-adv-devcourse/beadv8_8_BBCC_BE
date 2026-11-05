package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminPassword;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBootstrapService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public void bootstrap(String email, String password) {
        if (!StringUtils.hasText(email)) {
            log.info("첫 관리자 부트스트랩 건너뜀: 이메일 설정 없음");
            return;
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("첫 관리자 이메일은 있는데 비밀번호(kidly.auth.admin.bootstrap.password)가 비어 있습니다");
        }
        var passwordHash = passwordEncoder.encode(new AdminPassword(password).value());
        var inserted = adminAccountRepository.insertBootstrapIfNone(
            AdminAccount.normalizeEmail(email), passwordHash, LocalDateTime.now());
        if (inserted == 1) {
            log.info("첫 관리자 생성");
        } else {
            log.info("첫 관리자 부트스트랩 건너뜀: 관리자가 이미 있음");
        }
    }
}
