package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDefaultShippingAddressRepository
    extends JpaRepository<DefaultShippingAddress, Long>, DefaultShippingAddressRepository {
}
