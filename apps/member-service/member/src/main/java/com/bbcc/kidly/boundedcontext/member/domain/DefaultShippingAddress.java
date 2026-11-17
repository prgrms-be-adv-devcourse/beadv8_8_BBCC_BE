package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import com.bbcc.kidly.member.support.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Entity
@Table(name = "default_shipping_address", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefaultShippingAddress extends BaseTimeEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "shipping_address_id", nullable = false)
    private Long shippingAddressId;

    // id를 직접 넣으므로 Spring Data는 version이 null인지로 새 행을 판단한다. 새 행은 persist되어 같은 회원이면 PK 충돌로 실패한다
    @Version
    private Long version;

    private DefaultShippingAddress(Long memberId, Long shippingAddressId) {
        this.memberId = memberId;
        this.shippingAddressId = shippingAddressId;
    }

    public static DefaultShippingAddress designate(Long memberId, ShippingAddress shippingAddress) {
        validateOwner(memberId, shippingAddress);
        return new DefaultShippingAddress(memberId, shippingAddress.getId());
    }

    public void change(ShippingAddress shippingAddress) {
        validateOwner(memberId, shippingAddress);
        this.shippingAddressId = shippingAddress.getId();
    }

    public boolean isPointingTo(Long addressId) {
        return shippingAddressId.equals(addressId);
    }

    public void checkDeletable(Long addressId, long addressCount) {
        if (isPointingTo(addressId) && addressCount > 1) {
            throw new BusinessException(MemberErrorCode.DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE);
        }
    }

    private static void validateOwner(Long memberId, ShippingAddress shippingAddress) {
        Assert.isTrue(shippingAddress.isOwnedBy(memberId), "다른 회원의 배송지는 기본 배송지로 지정할 수 없습니다");
    }
}
