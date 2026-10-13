package com.bbcc.kidly.boundedcontext.payment.domain;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    boolean existsByMemberId(Long memberId);
}
