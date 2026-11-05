package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.GuardianVerificationService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.GuardianVerificationRequest;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.GuardianVerificationResponse;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guardian-verification")
@RequiredArgsConstructor
public class GuardianVerificationController {
    private final GuardianVerificationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuardianVerificationResponse verify(@LoginMember AuthMember member,
        @Valid @RequestBody GuardianVerificationRequest request, HttpServletResponse response) {
        var verification = service.verify(member.id(), request.identityVerificationId());
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/guardian-verification");
        return GuardianVerificationResponse.from(verification);
    }

    @GetMapping
    public GuardianVerificationResponse get(@LoginMember AuthMember member) {
        return GuardianVerificationResponse.from(service.find(member.id()));
    }
}
