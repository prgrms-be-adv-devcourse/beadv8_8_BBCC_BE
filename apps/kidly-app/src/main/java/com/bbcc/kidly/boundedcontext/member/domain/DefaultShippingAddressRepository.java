package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.Optional;

/**
 * 기본 배송지 ID는 회원 ID와 같다.
 */
public interface DefaultShippingAddressRepository {

    DefaultShippingAddress save(DefaultShippingAddress defaultShippingAddress);

    Optional<DefaultShippingAddress> findById(Long memberId);

    void delete(DefaultShippingAddress defaultShippingAddress);
}
