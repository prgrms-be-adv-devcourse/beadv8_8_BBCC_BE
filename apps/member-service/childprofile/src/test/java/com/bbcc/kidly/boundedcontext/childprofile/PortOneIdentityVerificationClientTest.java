package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.boundedcontext.childprofile.out.PortOneIdentityVerificationClient;
import com.bbcc.kidly.boundedcontext.childprofile.out.PortOneProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PortOneIdentityVerificationClientTest {
    private static final String URL = "https://api.portone.io/identity-verifications/test-id";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PortOneIdentityVerificationClient client =
        new PortOneIdentityVerificationClient(builder.build(), new PortOneProperties("secret"));

    @Test
    void convertsUtcToKoreaTime() {
        server.expect(requestTo(URL)).andExpect(header("Authorization", "PortOne secret"))
            .andRespond(withSuccess("{\"status\":\"VERIFIED\",\"verifiedAt\":\"2026-11-05T01:49:00.123Z\","
                + "\"verifiedCustomer\":{\"name\":\"홍길동\",\"phoneNumber\":\"01012345678\","
                + "\"birthDate\":\"1990-01-01\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.verify("test-id").verifiedAt())
            .isEqualTo(LocalDateTime.of(2026, 11, 5, 10, 49, 0, 123000000));
        server.verify();
    }

    @Test
    void rejectsReady() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"READY\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify("test-id")).extracting("errorCode")
            .isEqualTo(ChildProfileErrorCode.VERIFICATION_INCOMPLETE);
    }

    @Test
    void rejectsMissing() {
        server.expect(requestTo(URL)).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.verify("test-id")).extracting("errorCode")
            .isEqualTo(ChildProfileErrorCode.VERIFICATION_INCOMPLETE);
    }

    @Test
    void wrapsProviderError() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client.verify("test-id")).extracting("errorCode")
            .isEqualTo(ChildProfileErrorCode.VERIFICATION_PROVIDER_ERROR);
    }

    @Test
    void wrapsUnreadableResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"VERIFIED\",\"verifiedAt\":\"어제\"}",
            MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.verify("test-id")).extracting("errorCode")
            .isEqualTo(ChildProfileErrorCode.VERIFICATION_PROVIDER_ERROR);
    }
}
