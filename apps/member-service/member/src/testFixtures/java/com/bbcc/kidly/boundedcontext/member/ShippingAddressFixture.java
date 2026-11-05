package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressInfo;
import org.springframework.test.util.ReflectionTestUtils;

public final class ShippingAddressFixture {

    public static final Long MEMBER_ID = 1L;

    private ShippingAddressFixture() {
    }

    public static ShippingAddressInfo info() {
        return new ShippingAddressInfo("홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 212", "101동 1001호");
    }

    public static ShippingAddress address() {
        return address(MEMBER_ID);
    }

    public static ShippingAddress address(Long memberId) {
        return ShippingAddress.register(memberId, info());
    }

    // 리포지토리 없이 id가 필요한 단위 테스트용
    public static ShippingAddress addressWithId(Long id, Long memberId) {
        var address = address(memberId);
        ReflectionTestUtils.setField(address, "id", id);
        return address;
    }
}
