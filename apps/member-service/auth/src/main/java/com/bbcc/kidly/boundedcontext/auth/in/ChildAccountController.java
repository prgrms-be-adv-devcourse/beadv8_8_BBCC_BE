package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountCreateRequest;
import com.bbcc.kidly.boundedcontext.auth.in.dto.ChildAccountResponse;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ChildAccountPasswordRequest;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/child-accounts")
@RequiredArgsConstructor
public class ChildAccountController {
    private final ChildAccountService service;

    @GetMapping
    public List<ChildAccountResponse> list(@LoginMember AuthMember member) {
        return service.list(member.id()).stream().map(ChildAccountResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildAccountResponse create(@LoginMember AuthMember member,
        @Valid @RequestBody ChildAccountCreateRequest request) {
        return ChildAccountResponse.from(service.create(member.id(), request));
    }

    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@LoginMember AuthMember member, @PathVariable Long id,
        @Valid @RequestBody ChildAccountPasswordRequest request) {
        service.resetPassword(member.id(), id, request.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember AuthMember member, @PathVariable Long id) {
        service.delete(member.id(), id);
    }
}
