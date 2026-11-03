package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminResponse;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminPassword;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminResponse create(Long actorId, String email, String rawPassword) {
        AdminPassword password;
        try {
            password = new AdminPassword(rawPassword);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_ADMIN_PASSWORD, e.getMessage());
        }
        var hash = passwordEncoder.encode(password.value());
        var account = adminAccountRepository.saveAccount(AdminAccount.create(email, hash, actorId));
        return AdminResponse.from(account);
    }
}
