package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaShippingAddressRepository extends JpaRepository<ShippingAddress, Long>, ShippingAddressRepository {
}
