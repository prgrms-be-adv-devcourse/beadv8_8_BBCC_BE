package com.bbcc.kidly.boundedcontext.childprofile.out;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.IdentityVerificationClient;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class PortOneIdentityVerificationClient implements IdentityVerificationClient {
    private final RestClient portOneRestClient;
    private final PortOneProperties properties;

    @Override
    public VerifiedIdentity verify(String identityVerificationId) {
        try {
            var result = portOneRestClient.get()
                .uri("https://api.portone.io/identity-verifications/{id}", identityVerificationId)
                .header(HttpHeaders.AUTHORIZATION, "PortOne " + properties.apiSecret())
                .retrieve().body(Verification.class);
            if (!"VERIFIED".equals(result.status())) {
                throw new BusinessException(ChildProfileErrorCode.VERIFICATION_INCOMPLETE);
            }
            var customer = result.verifiedCustomer();
            var verifiedAt = LocalDateTime.ofInstant(result.verifiedAt(), ZoneId.of("Asia/Seoul"));
            return new VerifiedIdentity(customer.name(), customer.phoneNumber(), customer.birthDate(), verifiedAt);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ChildProfileErrorCode.VERIFICATION_INCOMPLETE);
        } catch (RestClientException e) {
            throw new BusinessException(ChildProfileErrorCode.VERIFICATION_PROVIDER_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verification(String status, Instant verifiedAt, Customer verifiedCustomer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Customer(String name, String phoneNumber, LocalDate birthDate) {
    }
}
