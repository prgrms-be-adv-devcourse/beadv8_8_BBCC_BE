package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.ShippingAddressResponse;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/addresses")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShippingAddressResponse register(
        @LoginMember AuthMember member, @Valid @RequestBody ShippingAddressRequest request,
        HttpServletResponse response) {
        var address = shippingAddressService.register(member.id(), request);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/members/me/addresses/" + address.id());
        return ShippingAddressResponse.from(address);
    }

    @GetMapping
    public List<ShippingAddressResponse> list(@LoginMember AuthMember member) {
        return shippingAddressService.getAddresses(member.id()).stream().map(ShippingAddressResponse::from).toList();
    }

    @PatchMapping("/{addressId}")
    public ShippingAddressResponse update(
        @LoginMember AuthMember member, @PathVariable Long addressId,
        @Valid @RequestBody ShippingAddressRequest request) {
        return ShippingAddressResponse.from(shippingAddressService.update(member.id(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember AuthMember member, @PathVariable Long addressId) {
        shippingAddressService.delete(member.id(), addressId);
    }

    @PatchMapping("/{addressId}/default")
    public ShippingAddressResponse changeDefault(@LoginMember AuthMember member, @PathVariable Long addressId) {
        return ShippingAddressResponse.from(shippingAddressService.changeDefault(member.id(), addressId));
    }
}
