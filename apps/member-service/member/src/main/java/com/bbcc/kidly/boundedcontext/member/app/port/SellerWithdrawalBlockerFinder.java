package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.domain.WithdrawalBlocker;
import java.util.List;

public interface SellerWithdrawalBlockerFinder {
    List<WithdrawalBlocker> find(Long sellerId);
}
