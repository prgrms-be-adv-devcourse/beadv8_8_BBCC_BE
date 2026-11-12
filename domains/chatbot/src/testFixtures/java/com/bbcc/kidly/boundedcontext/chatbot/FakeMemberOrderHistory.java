package com.bbcc.kidly.boundedcontext.chatbot;

import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import com.bbcc.kidly.shared.order.out.MemberOrderHistory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 주문 서비스 없이 쓰는 회원 주문 조회. 회원마다 정해 둔 주문을 limit개까지 주고 받은 (회원 ID, limit)을 남긴다.
 * 테스트가 끝나면 {@link #reset}으로 되돌린다.
 */
public class FakeMemberOrderHistory implements MemberOrderHistory {

    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<Long, List<MemberOrderSummary>> ordersByMember = new ConcurrentHashMap<>();
    private volatile RuntimeException failure;

    public void willReturn(Long memberId, List<MemberOrderSummary> orders) {
        ordersByMember.put(memberId, List.copyOf(orders));
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        ordersByMember.clear();
        failure = null;
    }

    @Override
    public List<MemberOrderSummary> recent(Long memberId, int limit) {
        requests.add(new Request(memberId, limit));
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        return ordersByMember.getOrDefault(memberId, List.of()).stream().limit(limit).toList();
    }

    public record Request(Long memberId, int limit) {
    }
}
