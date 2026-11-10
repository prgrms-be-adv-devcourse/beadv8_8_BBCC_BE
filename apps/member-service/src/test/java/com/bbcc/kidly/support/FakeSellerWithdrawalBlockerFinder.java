package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.member.app.port.SellerWithdrawalBlockerFinder;
import com.bbcc.kidly.boundedcontext.member.domain.WithdrawalBlocker;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FakeSellerWithdrawalBlockerFinder implements SellerWithdrawalBlockerFinder {
    private final Map<Long, List<WithdrawalBlocker>> blockers = new HashMap<>();
    private final Set<Long> failures = new HashSet<>();
    private final Set<Long> calls = new HashSet<>();

    public void setBlockers(Long id, List<WithdrawalBlocker> reasons) {
        blockers.put(id, reasons);
    }

    public void fail(Long id) {
        failures.add(id);
    }

    public boolean called(Long id) {
        return calls.contains(id);
    }

    public void clear() {
        blockers.clear();
        failures.clear();
        calls.clear();
    }

    @Override
    public List<WithdrawalBlocker> find(Long sellerId) {
        calls.add(sellerId);
        if (failures.contains(sellerId)) {
            throw new BusinessException(MemberErrorCode.SELLER_WITHDRAWAL_CHECK_UNAVAILABLE);
        }
        return blockers.getOrDefault(sellerId, List.of());
    }
}
