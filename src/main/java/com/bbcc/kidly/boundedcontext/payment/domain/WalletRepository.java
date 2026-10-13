package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.Optional;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    boolean existsByMemberId(Long memberId);

    Optional<Wallet> findByMemberId(Long memberId);
}
