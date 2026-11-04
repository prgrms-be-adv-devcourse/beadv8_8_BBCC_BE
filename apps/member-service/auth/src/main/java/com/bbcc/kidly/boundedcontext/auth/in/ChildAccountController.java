package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountResponse;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/child-accounts")
@RequiredArgsConstructor
public class ChildAccountController {
    private final ChildAccountService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildAccountResponse create(@LoginMember AuthMember member,
        @Valid @RequestBody ChildAccountCreateRequest request) {
        return service.create(member.id(), request);
    }
}
