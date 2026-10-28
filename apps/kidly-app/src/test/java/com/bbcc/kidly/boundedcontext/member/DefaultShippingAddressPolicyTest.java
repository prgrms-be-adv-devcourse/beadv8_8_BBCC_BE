package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.addressWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressPolicy;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultShippingAddressPolicyTest {

    @Mock
    private ShippingAddressRepository shippingAddressRepository;

    @Mock
    private DefaultShippingAddressRepository defaultShippingAddressRepository;

    @InjectMocks
    private DefaultShippingAddressPolicy policy;

    private static DefaultShippingAddress pointerTo(ShippingAddress address) {
        return DefaultShippingAddress.designate(MEMBER_ID, address);
    }

    @Test
    @DisplayName("기본 배송지 행이 없으면 requested=false여도 이 배송지를 가리키는 새 행을 돌려주고 저장하지 않는다")
    void applyDefaultPointerAbsent() {
        var address = addressWithId(10L, MEMBER_ID);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        var result = policy.applyDefault(address, false);

        assertThat(result.isPointingTo(10L)).isTrue();
        then(defaultShippingAddressRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("기본 배송지 행이 있고 requested=true면 그 행을 이 배송지로 바꿔 돌려준다")
    void applyDefaultRequested() {
        var pointer = pointerTo(addressWithId(10L, MEMBER_ID));
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));

        var result = policy.applyDefault(addressWithId(11L, MEMBER_ID), true);

        assertThat(result).isSameAs(pointer);
        assertThat(result.isPointingTo(11L)).isTrue();
        then(defaultShippingAddressRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("기본 배송지 행이 있고 requested=false면 그 행을 바꾸지 않고 돌려준다")
    void applyDefaultNotRequested() {
        var pointer = pointerTo(addressWithId(10L, MEMBER_ID));
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));

        var result = policy.applyDefault(addressWithId(11L, MEMBER_ID), false);

        assertThat(result).isSameAs(pointer);
        assertThat(result.isPointingTo(10L)).isTrue();
        then(defaultShippingAddressRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("배송지가 1개뿐인 기본 배송지면 함께 지울 기본 배송지 행을 돌려준다")
    void defaultToDeleteWithOnlyDefault() {
        var only = addressWithId(1L, MEMBER_ID);
        var pointer = pointerTo(only);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));
        given(shippingAddressRepository.countByMemberId(MEMBER_ID)).willReturn(1L);

        var result = policy.defaultToDeleteWith(only);

        assertThat(result).containsSame(pointer);
        then(defaultShippingAddressRepository).should(never()).delete(any());
        then(shippingAddressRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("배송지가 여러 개일 때 기본 배송지는 지울 수 없다")
    void defaultToDeleteWithDefaultAmongMany() {
        var current = addressWithId(1L, MEMBER_ID);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointerTo(current)));
        given(shippingAddressRepository.countByMemberId(MEMBER_ID)).willReturn(2L);

        assertThatThrownBy(() -> policy.defaultToDeleteWith(current))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE));
        then(defaultShippingAddressRepository).should(never()).delete(any());
        then(shippingAddressRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("기본이 아닌 배송지는 함께 지울 행이 없다")
    void defaultToDeleteWithNonDefault() {
        var current = addressWithId(1L, MEMBER_ID);
        var target = addressWithId(2L, MEMBER_ID);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointerTo(current)));
        given(shippingAddressRepository.countByMemberId(MEMBER_ID)).willReturn(2L);

        var result = policy.defaultToDeleteWith(target);

        assertThat(result).isEmpty();
        then(defaultShippingAddressRepository).should(never()).delete(any());
        then(shippingAddressRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("기본 배송지 행이 없으면 함께 지울 행이 없다")
    void defaultToDeleteWithPointerAbsent() {
        var target = addressWithId(1L, MEMBER_ID);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        var result = policy.defaultToDeleteWith(target);

        assertThat(result).isEmpty();
        then(defaultShippingAddressRepository).should(never()).delete(any());
        then(shippingAddressRepository).should(never()).delete(any());
    }
}
