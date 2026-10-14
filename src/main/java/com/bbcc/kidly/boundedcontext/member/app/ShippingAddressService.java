package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressResult;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 회원의 배송지를 등록 · 조회 · 수정 · 삭제하고 기본 배송지를 바꾼다. 본인 배송지만 다루고, 아니면 404다.
 * 배송지가 있으면 기본 배송지는 정확히 1개다. 동시 요청을 DB(PK · FK · version)가 막으면 전역 핸들러가 409로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class ShippingAddressService {

    // 기본 배송지가 먼저. 정렬이 안정적이라 나머지는 조회한 등록 순 그대로다
    private static final Comparator<ShippingAddressResult> DEFAULT_FIRST =
        Comparator.comparing(ShippingAddressResult::isDefault, Comparator.reverseOrder());

    private final ShippingAddressRepository shippingAddressRepository;
    private final DefaultShippingAddressRepository defaultShippingAddressRepository;

    // 첫 배송지는 isDefault와 상관없이 기본 배송지가 된다
    @Transactional
    public ShippingAddressResult register(Long memberId, ShippingAddressRequest request) {
        var address = shippingAddressRepository.save(ShippingAddress.register(memberId, request.toInfo()));
        var defaultAddress = findOrDesignateDefault(memberId, address);
        if (request.isDefault()) {
            defaultAddress.change(address);
        }
        return ShippingAddressResult.of(address, defaultAddress.isPointingTo(address.getId()));
    }

    @Transactional(readOnly = true)
    public List<ShippingAddressResult> getAddresses(Long memberId) {
        var defaultAddressId = defaultShippingAddressRepository.findById(memberId)
            .map(defaultAddress -> defaultAddress.getShippingAddress().getId())
            .orElse(null);
        return shippingAddressRepository.findAllByMemberIdOrderByIdAsc(memberId).stream()
            .map(address -> ShippingAddressResult.of(address, address.getId().equals(defaultAddressId)))
            .sorted(DEFAULT_FIRST)
            .toList();
    }

    @Transactional
    public ShippingAddressResult update(Long memberId, Long addressId, ShippingAddressRequest request) {
        var address = shippingAddressRepository.getByIdAndMemberId(addressId, memberId);
        address.update(request.toInfo());
        var defaultAddress = findOrDesignateDefault(memberId, address);
        if (request.isDefault()) {
            defaultAddress.change(address);
        }
        return ShippingAddressResult.of(address, defaultAddress.isPointingTo(address.getId()));
    }

    @Transactional
    public ShippingAddressResult changeDefault(Long memberId, Long addressId) {
        var address = shippingAddressRepository.getByIdAndMemberId(addressId, memberId);
        findOrDesignateDefault(memberId, address).change(address);
        return ShippingAddressResult.of(address, true);
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        var address = shippingAddressRepository.getByIdAndMemberId(addressId, memberId);
        var defaultAddress = defaultShippingAddressRepository.findById(memberId);
        defaultAddress.ifPresent(
            it -> it.checkDeletable(addressId, shippingAddressRepository.countByMemberId(memberId)));
        // 하나뿐인 기본 배송지를 지우는 경우. FK 때문에 기본 배송지 행을 먼저 지운다 (Hibernate는 지운 순서대로 DELETE한다)
        defaultAddress.filter(it -> it.isPointingTo(addressId)).ifPresent(defaultShippingAddressRepository::delete);
        shippingAddressRepository.delete(address);
    }

    // 기본 배송지 행이 없으면(첫 등록, 또는 동시 요청으로 빠진 경우) 이 배송지를 가리키도록 새로 만든다
    private DefaultShippingAddress findOrDesignateDefault(Long memberId, ShippingAddress address) {
        return defaultShippingAddressRepository.findById(memberId).orElseGet(() ->
                defaultShippingAddressRepository.save(DefaultShippingAddress.designate(memberId, address)));
    }
}
