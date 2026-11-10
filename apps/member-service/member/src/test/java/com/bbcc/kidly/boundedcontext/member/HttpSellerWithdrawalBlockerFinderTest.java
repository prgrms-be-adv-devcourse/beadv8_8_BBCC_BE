package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.boundedcontext.member.domain.WithdrawalBlocker;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.out.HttpSellerWithdrawalBlockerFinder;
import com.bbcc.kidly.boundedcontext.member.out.WithdrawalCheckProperties;
import com.bbcc.kidly.member.support.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import java.io.IOException;

class HttpSellerWithdrawalBlockerFinderTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpSellerWithdrawalBlockerFinder finder = new HttpSellerWithdrawalBlockerFinder(builder.build(),
        new WithdrawalCheckProperties("http://order", "http://settlement"));

    @Test
    void combinesOrderAndUnknownSettlementReasons() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(withSuccess("{\"sellerId\":12,\"openItemCount\":3}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://settlement/internal/sellers/12/withdrawal-blockers"))
            .andRespond(withSuccess("{\"blockers\":[{\"type\":\"NEW\",\"count\":2}]}", MediaType.APPLICATION_JSON));
        assertThat(finder.find(12L)).containsExactly(new WithdrawalBlocker("UNCONFIRMED_ORDER_ITEM", 3),
            new WithdrawalBlocker("NEW", 2));
        server.verify();
    }

    @Test
    void rejectsServerError() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary")).andRespond(withServerError());
        assertThatThrownBy(() -> finder.find(12L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE));
    }

    @Test
    void returnsEmptyWhenNeitherServiceHasBlockers() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(withSuccess("{\"openItemCount\":0,\"extra\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://settlement/internal/sellers/12/withdrawal-blockers"))
            .andRespond(withSuccess("{\"blockers\":[]}", MediaType.APPLICATION_JSON));
        assertThat(finder.find(12L)).isEmpty();
    }

    @Test
    void rejectsUnreadableResponse() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(withSuccess("잘못된 응답", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> finder.find(12L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE));
    }

    @Test
    void rejectsClientError() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(withSuccess("{\"openItemCount\":0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://settlement/internal/sellers/12/withdrawal-blockers"))
            .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> finder.find(12L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE));
    }

    @Test
    void rejectsSettlementFailure() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(withSuccess("{\"openItemCount\":0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://settlement/internal/sellers/12/withdrawal-blockers"))
            .andRespond(withServerError());
        assertThatThrownBy(() -> finder.find(12L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE));
    }

    @Test
    void rejectsMissingSettlementBlockers() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(withSuccess("{\"openItemCount\":0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://settlement/internal/sellers/12/withdrawal-blockers"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> finder.find(12L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE));
    }

    @Test
    void rejectsConnectionTimeout() {
        server.expect(requestTo("http://order/internal/sellers/12/order-items/summary"))
            .andRespond(request -> {
                throw new ResourceAccessException("시간 초과", new IOException());
            });
        assertThatThrownBy(() -> finder.find(12L)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE));
    }
}
