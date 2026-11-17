package com.bbcc.kidly.boundedcontext.member.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DefaultShippingAddressPolicy {

    private final ShippingAddressRepository shippingAddressRepository;
    private final DefaultShippingAddressRepository defaultShippingAddressRepository;

    public DefaultShippingAddress applyDefault(ShippingAddress address, boolean requested) {
        var memberId = address.getMemberId();
        var defaultAddress = defaultShippingAddressRepository.findById(memberId)
            .orElseGet(() -> DefaultShippingAddress.designate(memberId, address));
        if (requested) {
            defaultAddress.change(address);
        }
        return defaultAddress;
    }

    public Optional<DefaultShippingAddress> defaultToDeleteWith(ShippingAddress address) {
        var memberId = address.getMemberId();
        var defaultAddress = defaultShippingAddressRepository.findById(memberId);
        defaultAddress.ifPresent(
            it -> it.checkDeletable(address.getId(), shippingAddressRepository.countByMemberId(memberId)));
        return defaultAddress.filter(it -> it.isPointingTo(address.getId()));
    }
}
