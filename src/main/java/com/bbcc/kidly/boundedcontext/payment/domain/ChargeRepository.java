package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChargeRepository {

    Charge save(Charge charge);

    // 새 취소 요청의 ID를 바로 받아야 할 때 쓴다
    Charge saveAndFlush(Charge charge);

    Optional<Charge> findById(Long id);

    Optional<Charge> findByTossOrderId(String tossOrderId);

    // 상태를 바꿀 때 쓴다. 같은 충전을 바꾸는 다른 트랜잭션은 커밋될 때까지 기다린다
    Optional<Charge> findWithLockById(Long id);

    Optional<Charge> findWithLockByTossOrderId(String tossOrderId);

    List<Charge> findAllByStatusAndUpdatedAtBefore(ChargeStatus status, LocalDateTime before);

    List<Charge> findAllByStatusAndExpiresAtBefore(ChargeStatus status, LocalDateTime before);

    Page<Charge> findAllByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);
}
