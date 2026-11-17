package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerApplicationResponse;
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
@RequestMapping(SellerApplicationController.PATH)
@RequiredArgsConstructor
public class SellerApplicationController {

    static final String PATH = "/api/v1/members/me/seller-application";

    private final SellerApplicationService sellerApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SellerApplicationResponse apply(@LoginMember AuthMember member,
        @Valid @RequestBody SellerApplicationRequest request, HttpServletResponse response) {
        var application = sellerApplicationService.submit(member.id(), member.nickname(), request);
        response.setHeader(HttpHeaders.LOCATION, PATH);
        return SellerApplicationResponse.from(application);
    }

    @GetMapping
    public SellerApplicationResponse getLatest(@LoginMember AuthMember member) {
        return SellerApplicationResponse.from(sellerApplicationService.getLatest(member.id()));
    }
}
