package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShippingAddressTest {

    private static ShippingAddressInfo withDetail(String v) {
        var base = ShippingAddressFixture.info();
        return new ShippingAddressInfo(base.recipientName(), base.recipientPhone(), base.zipCode(),
            base.roadAddress(), v);
    }

    @Test
    @DisplayName("등록하면 회원 id와 입력한 다섯 값이 저장된다")
    void registerStoresValues() {
        var address = ShippingAddressFixture.address();

        assertThat(address.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(address.getRecipientName()).isEqualTo("홍길동");
        assertThat(address.getRecipientPhone()).isEqualTo("010-1234-5678");
        assertThat(address.getZipCode()).isEqualTo("06236");
        assertThat(address.getRoadAddress()).isEqualTo("서울 강남구 테헤란로 212");
        assertThat(address.getDetailAddress()).isEqualTo("101동 1001호");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("상세 주소가 없거나 비어 있으면 null로 등록된다")
    void registerStoresBlankDetailAddressAsNull(String blank) {
        var address = ShippingAddress.register(MEMBER_ID, withDetail(blank));

        assertThat(address.getDetailAddress()).isNull();
    }

    @Test
    @DisplayName("수정하면 다섯 값이 모두 바뀌고 회원 id는 그대로다")
    void updateReplacesAll() {
        var address = ShippingAddressFixture.address();

        address.update(new ShippingAddressInfo("김철수", "02-123-4567", "12345", "부산 해운대구 해운대로 1", null));

        assertThat(address.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(address.getRecipientName()).isEqualTo("김철수");
        assertThat(address.getRecipientPhone()).isEqualTo("02-123-4567");
        assertThat(address.getZipCode()).isEqualTo("12345");
        assertThat(address.getRoadAddress()).isEqualTo("부산 해운대구 해운대로 1");
        assertThat(address.getDetailAddress()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("상세 주소가 비어 있으면 null로 수정된다")
    void updateStoresBlankDetailAddressAsNull(String blank) {
        var address = ShippingAddressFixture.address();

        address.update(withDetail(blank));

        assertThat(address.getDetailAddress()).isNull();
    }

    @Test
    @DisplayName("같은 회원의 배송지만 본인 소유로 판단한다")
    void isOwnedBy() {
        var address = ShippingAddressFixture.address(MEMBER_ID);

        assertThat(address.isOwnedBy(MEMBER_ID)).isTrue();
        assertThat(address.isOwnedBy(2L)).isFalse();
    }
}
