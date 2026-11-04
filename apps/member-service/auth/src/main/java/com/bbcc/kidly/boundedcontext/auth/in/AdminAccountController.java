package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.AdminAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AdminResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/admins")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse create(@LoginAdmin AuthAdmin actor, @Valid @RequestBody AdminCreateRequest request,
        HttpServletResponse response) {
        var account = adminAccountService.create(actor.id(), request.email(), request.password());
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/admins/" + account.id());
        return account;
    }

    @PostMapping("/{adminId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@LoginAdmin AuthAdmin actor, @PathVariable Long adminId) {
        adminAccountService.disable(actor.id(), adminId);
    }
}
