package com.bbcc.kidly.boundedcontext.payment.domain;

import java.util.Optional;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    boolean existsByMemberId(Long memberId);

    Optional<Wallet> findByMemberId(Long memberId);

    // 잔액을 바꿀 때 쓴다. 같은 지갑을 바꾸는 다른 트랜잭션은 커밋될 때까지 기다린다
    Optional<Wallet> findWithLockByMemberId(Long memberId);

    Optional<Wallet> findWithLockById(Long id);
}
