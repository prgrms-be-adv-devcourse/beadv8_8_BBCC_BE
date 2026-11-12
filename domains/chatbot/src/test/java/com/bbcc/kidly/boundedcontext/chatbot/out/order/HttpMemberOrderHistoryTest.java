package com.bbcc.kidly.boundedcontext.chatbot.out.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class HttpMemberOrderHistoryTest {

    private static final String ORDERS = "http://order/internal/members/7/orders?limit=5";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpMemberOrderHistory history = new HttpMemberOrderHistory(builder.build(),
        new ChatbotOrderProperties("http://order"));

    @Test
    @DisplayName("계약 칸만 읽는다: 주문 쪽이 배송지 · 송장번호 · 금액 같은 칸을 더해 보내도 결과에 남지 않는다")
    void readsContractFieldsOnly() {
        server.expect(requestTo(ORDERS))
            .andRespond(withSuccess("""
                [{"orderId":31,"orderedAt":"2026-11-11T09:05:00","status":"PAID","totalAmount":50000,
                  "recipientName":"홍길동","phone":"010-1234-5678","address":"서울시 테스트로 1",
                  "items":[{"productName":"여아 원피스","optionName":"110","quantity":2,"status":"ORDERED","unitPrice":25000,
                    "delivery":{"status":"SHIPPING","carrier":"CJ","trackingNumber":"987654321098",
                      "shippedAt":"2026-11-11T15:20:00","deliveredAt":null}}]}]""", MediaType.APPLICATION_JSON));

        List<MemberOrderSummary> orders = history.recent(7L, 5);

        assertThat(orders).containsExactly(new MemberOrderSummary(31L, LocalDateTime.of(2026, 11, 11, 9, 5), "PAID",
            List.of(new MemberOrderSummary.Item("여아 원피스", "110", 2, "ORDERED", new MemberOrderSummary.Delivery(
                "SHIPPING", "CJ", LocalDateTime.of(2026, 11, 11, 15, 20), null)))));
        assertThat(orders.toString()).doesNotContain("홍길동", "010-1234-5678", "테스트로", "987654321098", "50000");
        server.verify();
    }

    @Test
    @DisplayName("주문이 없으면 빈 목록이다")
    void empty() {
        server.expect(requestTo(ORDERS)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(history.recent(7L, 5)).isEmpty();
    }

    @Test
    @DisplayName("오류 응답 · 시간 초과 · 읽을 수 없는 응답은 IllegalStateException이고 메시지에 회원 ID가 없다")
    void failures() {
        server.expect(requestTo(ORDERS)).andRespond(withServerError());
        server.expect(requestTo(ORDERS)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(ORDERS)).andRespond(request -> {
            throw new ResourceAccessException("시간 초과", new IOException());
        });
        server.expect(requestTo(ORDERS)).andRespond(withSuccess("잘못된 응답", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ORDERS)).andRespond(withSuccess("[{\"status\":\"PAID\",\"items\":[]}]",
            MediaType.APPLICATION_JSON));
        server.expect(requestTo(ORDERS)).andRespond(withSuccess("[{\"orderId\":1,\"items\":[{\"quantity\":1}]}]",
            MediaType.APPLICATION_JSON));
        server.expect(requestTo(ORDERS)).andRespond(withSuccess("[null]", MediaType.APPLICATION_JSON));

        for (int i = 0; i < 7; i++) {
            assertThatThrownBy(() -> history.recent(7L, 5))
                .isInstanceOf(IllegalStateException.class)
                .message().doesNotContain("7");
        }
        server.verify();
    }
}
