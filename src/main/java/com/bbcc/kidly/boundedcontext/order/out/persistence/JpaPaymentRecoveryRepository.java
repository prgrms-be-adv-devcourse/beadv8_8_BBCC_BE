package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 결제 완료 반영 실패 기록 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaPaymentRecoveryRepository extends Repository<PaymentRecovery, Long>, PaymentRecoveryRepository {

    /**
     * SELECT … FOR UPDATE. 잠금 대기 시간은 따로 두지 않는다(앞 트랜잭션이 끝나면 바로 풀림).
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRecovery r where r.id = :id")
    Optional<PaymentRecovery> findByIdForUpdate(Long id);
}
