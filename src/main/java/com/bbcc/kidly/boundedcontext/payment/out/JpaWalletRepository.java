package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWalletRepository extends JpaRepository<Wallet, Long>, WalletRepository {
}
