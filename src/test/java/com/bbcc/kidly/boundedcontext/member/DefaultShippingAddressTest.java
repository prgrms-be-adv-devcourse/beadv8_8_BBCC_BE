package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultShippingAddressTest {

    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long DEFAULT_ADDRESS_ID = 10L;
    private static final Long OTHER_ADDRESS_ID = 11L;

    private static DefaultShippingAddress defaultAddress() {
        return DefaultShippingAddress.designate(
            MEMBER_ID, ShippingAddressFixture.addressWithId(DEFAULT_ADDRESS_ID, MEMBER_ID));
    }

    @Test
    @DisplayName("회원 ID와 배송지를 가지고 지정된다")
    void designate() {
        var address = ShippingAddressFixture.addressWithId(DEFAULT_ADDRESS_ID, MEMBER_ID);

        var defaultAddress = DefaultShippingAddress.designate(MEMBER_ID, address);

        assertThat(defaultAddress.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(defaultAddress.getShippingAddressId()).isEqualTo(DEFAULT_ADDRESS_ID);
    }

    @Test
    @DisplayName("다른 회원의 배송지는 기본 배송지로 지정할 수 없다")
    void designateOtherMembersAddress() {
        var address = ShippingAddressFixture.addressWithId(DEFAULT_ADDRESS_ID, OTHER_MEMBER_ID);

        assertThatThrownBy(() -> DefaultShippingAddress.designate(MEMBER_ID, address))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("회원 ID나 배송지가 null이면 지정할 수 없다")
    void designateNull() {
        var address = ShippingAddressFixture.addressWithId(DEFAULT_ADDRESS_ID, MEMBER_ID);

        assertThatThrownBy(() -> DefaultShippingAddress.designate(null, address))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DefaultShippingAddress.designate(MEMBER_ID, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("저장되지 않아 ID가 없는 배송지는 지정하거나 바꿀 수 없다")
    void designateUnsaved() {
        var unsaved = ShippingAddressFixture.address(MEMBER_ID);
        var defaultAddress = defaultAddress();

        assertThatThrownBy(() -> DefaultShippingAddress.designate(MEMBER_ID, unsaved))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> defaultAddress.change(unsaved))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("기본 배송지를 다른 배송지로 바꾼다")
    void change() {
        var defaultAddress = defaultAddress();
        var newAddress = ShippingAddressFixture.addressWithId(OTHER_ADDRESS_ID, MEMBER_ID);

        defaultAddress.change(newAddress);

        assertThat(defaultAddress.getShippingAddressId()).isEqualTo(OTHER_ADDRESS_ID);
    }

    @Test
    @DisplayName("배송지가 null이면 바꿀 수 없다")
    void changeNull() {
        var defaultAddress = defaultAddress();

        assertThatThrownBy(() -> defaultAddress.change(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("다른 회원의 배송지로는 바꿀 수 없고 기존 배송지가 유지된다")
    void changeToOtherMembersAddress() {
        var original = ShippingAddressFixture.addressWithId(DEFAULT_ADDRESS_ID, MEMBER_ID);
        var defaultAddress = DefaultShippingAddress.designate(MEMBER_ID, original);

        var otherMembers = ShippingAddressFixture.addressWithId(OTHER_ADDRESS_ID, OTHER_MEMBER_ID);

        assertThatThrownBy(() -> defaultAddress.change(otherMembers))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(defaultAddress.getShippingAddressId()).isEqualTo(DEFAULT_ADDRESS_ID);
    }

    @Test
    @DisplayName("가리키는 배송지 ID에만 true를 반환한다")
    void isPointingTo() {
        var defaultAddress = defaultAddress();

        assertThat(defaultAddress.isPointingTo(DEFAULT_ADDRESS_ID)).isTrue();
        assertThat(defaultAddress.isPointingTo(OTHER_ADDRESS_ID)).isFalse();
    }

    @Test
    @DisplayName("기본 배송지가 아닌 배송지는 삭제할 수 있다")
    void checkDeletableNonDefault() {
        var defaultAddress = defaultAddress();

        assertThatCode(() -> defaultAddress.checkDeletable(OTHER_ADDRESS_ID, 2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배송지가 1개뿐이면 기본 배송지도 삭제할 수 있다")
    void checkDeletableOnlyOne() {
        var defaultAddress = defaultAddress();

        assertThatCode(() -> defaultAddress.checkDeletable(DEFAULT_ADDRESS_ID, 1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("배송지가 여러 개면 기본 배송지는 삭제할 수 없다")
    void checkDeletableDefaultWithOthers() {
        var defaultAddress = defaultAddress();

        assertThatThrownBy(() -> defaultAddress.checkDeletable(DEFAULT_ADDRESS_ID, 2))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE));
    }
}
