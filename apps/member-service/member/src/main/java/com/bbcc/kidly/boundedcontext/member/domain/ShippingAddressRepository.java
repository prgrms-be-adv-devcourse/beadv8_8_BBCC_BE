package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

public interface ShippingAddressRepository {

    ShippingAddress save(ShippingAddress address);

    Optional<ShippingAddress> findByIdAndMemberId(Long id, Long memberId);

    List<ShippingAddress> findAllByMemberIdOrderByIdAsc(Long memberId);

    long countByMemberId(Long memberId);

    void delete(ShippingAddress address);

    void deleteAllByMemberId(Long memberId);

    default ShippingAddress getByIdAndMemberId(Long id, Long memberId) {
        return findByIdAndMemberId(id, memberId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
