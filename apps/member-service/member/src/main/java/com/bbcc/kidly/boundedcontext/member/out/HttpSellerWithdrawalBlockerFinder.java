package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.app.port.SellerWithdrawalBlockerFinder;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.WithdrawalBlocker;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class HttpSellerWithdrawalBlockerFinder implements SellerWithdrawalBlockerFinder {

    private final RestClient withdrawalRestClient;
    private final WithdrawalCheckProperties properties;

    // 실패 · 타임아웃 · 읽을 수 없는 응답은 확인하지 못한 것이므로 탈퇴를 통과시키지 않는다(503)
    @Override
    public List<WithdrawalBlocker> find(Long sellerId) {
        try {
            var order = withdrawalRestClient.get()
                .uri(properties.orderUri() + "/internal/sellers/{sellerId}/order-items/summary", sellerId)
                .retrieve().body(OrderSummary.class);
            var settlement = withdrawalRestClient.get()
                .uri(properties.settlementUri() + "/internal/sellers/{sellerId}/withdrawal-blockers", sellerId)
                .retrieve().body(SettlementBlockers.class);
            if (!isReadable(order) || !isReadable(settlement)) {
                throw unavailable();
            }
            var blockers = new ArrayList<WithdrawalBlocker>();
            if (order.openItemCount() > 0) {
                blockers.add(new WithdrawalBlocker(WithdrawalBlocker.UNCONFIRMED_ORDER_ITEM, order.openItemCount()));
            }
            settlement.blockers()
                .forEach(blocker -> blockers.add(new WithdrawalBlocker(blocker.type(), blocker.count())));
            return List.copyOf(blockers);
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private static boolean isReadable(OrderSummary order) {
        return order != null && order.openItemCount() != null;
    }

    private static boolean isReadable(SettlementBlockers settlement) {
        return settlement != null && settlement.blockers() != null
            && settlement.blockers().stream().allMatch(SettlementBlocker::isReadable);
    }

    private static BusinessException unavailable() {
        return new BusinessException(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE);
    }

    private record OrderSummary(Long openItemCount) {
    }

    private record SettlementBlockers(List<SettlementBlocker> blockers) {
    }

    private record SettlementBlocker(String type, Long count) {

        boolean isReadable() {
            return type != null && count != null;
        }
    }
}
