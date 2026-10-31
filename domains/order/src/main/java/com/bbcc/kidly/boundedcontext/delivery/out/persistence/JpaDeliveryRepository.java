package com.bbcc.kidly.boundedcontext.delivery.out.persistence;

import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import org.springframework.data.repository.Repository;

/**
 * 배송 저장소 포트를 Spring Data JPA로 구현한다. 포트에 선언한 메서드만 노출한다.
 */
public interface JpaDeliveryRepository extends Repository<Delivery, Long>, DeliveryRepository {
}
