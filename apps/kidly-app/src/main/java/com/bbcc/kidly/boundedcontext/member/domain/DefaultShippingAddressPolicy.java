package com.bbcc.kidly.boundedcontext.member.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 회원의 배송지가 있으면 기본 배송지는 정확히 1개이게 하는 도메인 서비스.
 * 배송지와 기본 배송지 두 애그리거트에 걸친 규칙이라 엔티티 대신 여기에 둔다.
 * 규칙만 정하고 조회만 한다. 저장 · 삭제 · 트랜잭션은 호출하는 서비스가 한다.
 * 동시 요청은 DB(기본 배송지 PK · FK · version)가 막는다.
 */
@Component
@RequiredArgsConstructor
public class DefaultShippingAddressPolicy {

    private final ShippingAddressRepository shippingAddressRepository;
    private final DefaultShippingAddressRepository defaultShippingAddressRepository;

    // 이 배송지를 등록 · 수정한 뒤의 기본 배송지를 돌려준다. 저장은 호출하는 쪽이 한다
    // 기본 배송지 행이 없으면(첫 등록, 또는 동시 요청으로 빠진 경우) requested와 상관없이 이 배송지를 가리키는 새 행이다
    // 있으면 requested일 때만 이 배송지로 바꾼다
    public DefaultShippingAddress applyDefault(ShippingAddress address, boolean requested) {
        var memberId = address.getMemberId();
        var defaultAddress = defaultShippingAddressRepository.findById(memberId)
            .orElseGet(() -> DefaultShippingAddress.designate(memberId, address));
        if (requested) {
            defaultAddress.change(address);
        }
        return defaultAddress;
    }

    // 기본 배송지는 지울 수 없다(409). 배송지가 1개뿐이면 지울 수 있고, 이때 함께 지워야 하는 기본 배송지 행을 돌려준다
    public Optional<DefaultShippingAddress> defaultToDeleteWith(ShippingAddress address) {
        var memberId = address.getMemberId();
        var defaultAddress = defaultShippingAddressRepository.findById(memberId);
        defaultAddress.ifPresent(
            it -> it.checkDeletable(address.getId(), shippingAddressRepository.countByMemberId(memberId)));
        return defaultAddress.filter(it -> it.isPointingTo(address.getId()));
    }
}
