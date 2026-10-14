package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedger;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWalletLedgerRepository extends JpaRepository<WalletLedger, Long>, WalletLedgerRepository {
}
