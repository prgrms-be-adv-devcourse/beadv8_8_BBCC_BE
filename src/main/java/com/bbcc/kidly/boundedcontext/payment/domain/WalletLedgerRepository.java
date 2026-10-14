package com.bbcc.kidly.boundedcontext.payment.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletLedgerRepository {

    WalletLedger save(WalletLedger walletLedger);

    Page<WalletLedger> findAllByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    boolean existsByTypeAndRefId(WalletLedgerType type, Long refId);
}
