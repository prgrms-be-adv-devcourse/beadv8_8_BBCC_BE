package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

/**
 * 조회는 항상 회원 ID와 함께 한다. 없는 배송지와 남의 배송지를 구분하지 않고 모두 없는 것으로 본다.
 */
public interface ShippingAddressRepository {

    ShippingAddress save(ShippingAddress address);

    Optional<ShippingAddress> findByIdAndMemberId(Long id, Long memberId);

    // 등록 순
    List<ShippingAddress> findAllByMemberIdOrderByIdAsc(Long memberId);

    long countByMemberId(Long memberId);

    void delete(ShippingAddress address);

    default ShippingAddress getByIdAndMemberId(Long id, Long memberId) {
        return findByIdAndMemberId(id, memberId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
