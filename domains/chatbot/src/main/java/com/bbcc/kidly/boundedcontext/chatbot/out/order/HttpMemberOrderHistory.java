package com.bbcc.kidly.boundedcontext.chatbot.out.order;

import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import com.bbcc.kidly.shared.order.out.MemberOrderHistory;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 주문 서비스의 내부 경로(GET /internal/members/{memberId}/orders)로 {@link MemberOrderHistory}를 부른다. 응답은 계약 칸만 읽어
 * 주문 쪽이 칸을 더해도 여기서 밖으로 나가지 않는다. 실패 · 시간 초과 · 읽을 수 없는 응답은 {@link IllegalStateException}으로 알리고,
 * 예외 메시지에 회원 ID를 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class HttpMemberOrderHistory implements MemberOrderHistory {

    private static final String PATH = "/internal/members/{memberId}/orders?limit={limit}";

    private final RestClient chatbotOrderRestClient;
    private final ChatbotOrderProperties properties;

    @Override
    public List<MemberOrderSummary> recent(Long memberId, int limit) {
        MemberOrderSummary[] orders;
        try {
            orders = chatbotOrderRestClient.get()
                .uri(properties.uri() + PATH, memberId, limit)
                .retrieve()
                .body(MemberOrderSummary[].class);
        } catch (RestClientException e) {
            throw new IllegalStateException("회원 주문 조회 호출 실패", e);
        }
        if (orders == null || !Arrays.stream(orders).allMatch(HttpMemberOrderHistory::isReadable)) {
            throw new IllegalStateException("회원 주문 조회 응답을 읽을 수 없습니다");
        }
        return List.of(orders);
    }

    private static boolean isReadable(MemberOrderSummary order) {
        return order != null && order.orderId() != null && order.items() != null
            && order.items().stream().allMatch(item -> item != null && item.productName() != null);
    }
}
