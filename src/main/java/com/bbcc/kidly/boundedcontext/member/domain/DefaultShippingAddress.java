package com.bbcc.kidly.boundedcontext.member.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 회원의 기본 배송지를 가리키는 포인터. PK가 회원 ID라 회원당 최대 1행이고,
 * FK 때문에 가리키는 배송지는 지울 수 없다. 배송지가 있으면 정확히 1행, 없으면 0행이다.
 */
@Entity
@Table(name = "default_shipping_address", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefaultShippingAddress extends BaseTimeEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipping_address_id", nullable = false)
    private ShippingAddress shippingAddress;

    // id를 직접 넣으므로 Spring Data는 version이 null인지로 새 행을 판단한다. 새 행은 persist되어 같은 회원이면 PK 충돌로 실패한다
    @Version
    private Long version;

    private DefaultShippingAddress(Long memberId, ShippingAddress shippingAddress) {
        this.memberId = memberId;
        this.shippingAddress = shippingAddress;
    }

    public static DefaultShippingAddress designate(Long memberId, ShippingAddress shippingAddress) {
        requireNonNull(memberId, "memberId는 필수입니다");
        validateOwner(memberId, shippingAddress);
        return new DefaultShippingAddress(memberId, shippingAddress);
    }

    public void change(ShippingAddress shippingAddress) {
        validateOwner(memberId, shippingAddress);
        this.shippingAddress = shippingAddress;
    }

    public boolean isPointingTo(Long addressId) {
        return shippingAddress.getId().equals(addressId);
    }

    // 기본 배송지는 지울 수 없다. 배송지가 1개뿐이면 예외로 지울 수 있고, 이때 이 행도 함께 지운다
    public void checkDeletable(Long addressId, long addressCount) {
        if (isPointingTo(addressId) && addressCount > 1) {
            throw new BusinessException(MemberErrorCode.DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE);
        }
    }

    private static void validateOwner(Long memberId, ShippingAddress shippingAddress) {
        requireNonNull(shippingAddress, "shippingAddress는 필수입니다");
        Assert.isTrue(shippingAddress.isOwnedBy(memberId), "다른 회원의 배송지는 기본 배송지로 지정할 수 없습니다");
    }
}
