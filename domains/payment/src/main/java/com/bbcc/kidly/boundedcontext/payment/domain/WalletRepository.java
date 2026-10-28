package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    boolean existsByMemberId(Long memberId);

    // 지갑이 없으면 잔액 0으로 만든다. 이미 있으면(동시에 만든 경우 포함) 아무것도 하지 않는다
    void createIfAbsent(Long memberId, LocalDateTime now);

    Optional<Wallet> findByMemberId(Long memberId);

    // 잔액을 바꿀 때 쓴다. 같은 지갑을 바꾸는 다른 트랜잭션은 커밋될 때까지 기다린다
    Optional<Wallet> findWithLockByMemberId(Long memberId);

    Optional<Wallet> findWithLockById(Long id);
}
