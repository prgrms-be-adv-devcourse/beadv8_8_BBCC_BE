package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 배송지. 회원(memberId)은 바뀌지 않는다. 기본 배송지 여부는 저장하지 않고 {@link DefaultShippingAddress}가 가리킨다.
 * 기본 배송지가 FK로 가리키므로 삭제된 배송지를 가리키지 않게 하려고 물리 삭제한다.
 */
@Entity
@Table(
    name = "shipping_address",
    schema = "member",
    indexes = @Index(name = "idx_shipping_address_member_id", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "recipient_name", nullable = false, length = MemberPolicy.RECIPIENT_NAME_MAX_LENGTH)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = MemberPolicy.RECIPIENT_PHONE_MAX_LENGTH)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false, length = MemberPolicy.ZIP_CODE_MAX_LENGTH)
    private String zipCode;

    @Column(name = "road_address", nullable = false, length = MemberPolicy.ROAD_ADDRESS_MAX_LENGTH)
    private String roadAddress;

    @Column(name = "detail_address", length = MemberPolicy.DETAIL_ADDRESS_MAX_LENGTH)
    private String detailAddress;

    private ShippingAddress(Long memberId) {
        this.memberId = memberId;
    }

    public static ShippingAddress register(Long memberId, ShippingAddressInfo info) {
        var address = new ShippingAddress(memberId);
        address.update(info);
        return address;
    }

    // 모든 값을 받는 전체 교체다
    public void update(ShippingAddressInfo info) {
        this.recipientName = info.recipientName();
        this.recipientPhone = info.recipientPhone();
        this.zipCode = info.zipCode();
        this.roadAddress = info.roadAddress();
        this.detailAddress = StringUtils.hasText(info.detailAddress()) ? info.detailAddress() : null;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
