package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.Optional;

public interface DefaultShippingAddressRepository {

    DefaultShippingAddress save(DefaultShippingAddress defaultShippingAddress);

    Optional<DefaultShippingAddress> findById(Long memberId);

    void delete(DefaultShippingAddress defaultShippingAddress);
}
