package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import org.springframework.data.repository.Repository;

/**
 * 결제 완료 반영 실패 기록 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaPaymentRecoveryRepository extends Repository<PaymentRecovery, Long>, PaymentRecoveryRepository {
}
