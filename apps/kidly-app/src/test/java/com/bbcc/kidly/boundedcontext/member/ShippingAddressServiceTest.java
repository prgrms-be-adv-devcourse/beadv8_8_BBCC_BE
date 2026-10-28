package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.addressWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressResult;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressPolicy;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShippingAddressServiceTest {

    @Mock
    private ShippingAddressRepository shippingAddressRepository;

    @Mock
    private DefaultShippingAddressRepository defaultShippingAddressRepository;

    private ShippingAddressService service;

    @BeforeEach
    void setUp() {
        service = new ShippingAddressService(shippingAddressRepository, defaultShippingAddressRepository,
            new DefaultShippingAddressPolicy(shippingAddressRepository, defaultShippingAddressRepository));
    }

    private static ShippingAddressRequest request(boolean isDefault) {
        var info = ShippingAddressFixture.info();
        return new ShippingAddressRequest(info.recipientName(), info.recipientPhone(), info.zipCode(),
            info.roadAddress(), info.detailAddress(), isDefault);
    }

    private static DefaultShippingAddress pointerTo(ShippingAddress address) {
        return DefaultShippingAddress.designate(MEMBER_ID, address);
    }

    private void stubSaveAssigningId(Long id) {
        given(shippingAddressRepository.save(any(ShippingAddress.class))).willAnswer(inv -> {
            ShippingAddress saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", id);
            return saved;
        });
    }

    private void stubDefaultSaveReturningArgument() {
        given(defaultShippingAddressRepository.save(any(DefaultShippingAddress.class)))
            .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("첫 배송지를 등록하면 isDefault와 상관없이 기본 배송지가 된다")
    void registerFirstBecomesDefault() {
        stubSaveAssigningId(10L);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        stubDefaultSaveReturningArgument();

        var result = service.register(MEMBER_ID, request(false));

        var captor = ArgumentCaptor.forClass(DefaultShippingAddress.class);
        then(defaultShippingAddressRepository).should().save(captor.capture());
        assertThat(captor.getValue().isPointingTo(10L)).isTrue();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("두 번째 배송지를 isDefault=false로 등록하면 기본 배송지는 그대로다")
    void registerSecondNotDefault() {
        stubSaveAssigningId(11L);
        var pointer = pointerTo(addressWithId(10L, MEMBER_ID));
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));
        stubDefaultSaveReturningArgument();

        var result = service.register(MEMBER_ID, request(false));

        assertThat(pointer.isPointingTo(10L)).isTrue();
        assertThat(result.isDefault()).isFalse();
        then(defaultShippingAddressRepository).should().save(pointer);
    }

    @Test
    @DisplayName("두 번째 배송지를 isDefault=true로 등록하면 기본 배송지가 바뀐다")
    void registerSecondDefault() {
        stubSaveAssigningId(11L);
        var pointer = pointerTo(addressWithId(10L, MEMBER_ID));
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));
        stubDefaultSaveReturningArgument();

        var result = service.register(MEMBER_ID, request(true));

        assertThat(pointer.isPointingTo(11L)).isTrue();
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("목록은 기본 배송지가 먼저, 나머지는 등록 순이다")
    void getAddressesDefaultFirst() {
        var first = addressWithId(1L, MEMBER_ID);
        var second = addressWithId(2L, MEMBER_ID);
        var third = addressWithId(3L, MEMBER_ID);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointerTo(second)));
        given(shippingAddressRepository.findAllByMemberIdOrderByIdAsc(MEMBER_ID))
            .willReturn(List.of(first, second, third));

        var results = service.getAddresses(MEMBER_ID);

        assertThat(results).extracting(ShippingAddressResult::id, ShippingAddressResult::isDefault)
            .containsExactly(tuple(2L, true), tuple(1L, false), tuple(3L, false));
    }

    @Test
    @DisplayName("기본 배송지 행이 없으면 목록의 모든 배송지가 기본이 아니다")
    void getAddressesNoPointer() {
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        given(shippingAddressRepository.findAllByMemberIdOrderByIdAsc(MEMBER_ID))
            .willReturn(List.of(addressWithId(1L, MEMBER_ID), addressWithId(2L, MEMBER_ID)));

        var results = service.getAddresses(MEMBER_ID);

        assertThat(results).extracting(ShippingAddressResult::id, ShippingAddressResult::isDefault)
            .containsExactly(tuple(1L, false), tuple(2L, false));
    }

    @Test
    @DisplayName("수정 시 isDefault=true면 기본 배송지가 바뀌고 내용이 교체된다")
    void updateDefault() {
        var current = addressWithId(1L, MEMBER_ID);
        var target = addressWithId(2L, MEMBER_ID);
        var pointer = pointerTo(current);
        given(shippingAddressRepository.getByIdAndMemberId(2L, MEMBER_ID)).willReturn(target);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));
        stubDefaultSaveReturningArgument();
        var request = request(true);

        var result = service.update(MEMBER_ID, 2L, request);

        assertThat(pointer.isPointingTo(2L)).isTrue();
        assertThat(result.isDefault()).isTrue();
        assertThat(result.recipientName()).isEqualTo(request.recipientName());
    }

    @Test
    @DisplayName("수정 시 isDefault=false면 기존 기본 배송지를 유지한다")
    void updateNotDefault() {
        var current = addressWithId(1L, MEMBER_ID);
        var target = addressWithId(2L, MEMBER_ID);
        var pointer = pointerTo(current);
        given(shippingAddressRepository.getByIdAndMemberId(2L, MEMBER_ID)).willReturn(target);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));
        stubDefaultSaveReturningArgument();

        var result = service.update(MEMBER_ID, 2L, request(false));

        assertThat(pointer.isPointingTo(1L)).isTrue();
        assertThat(result.isDefault()).isFalse();
    }

    @Test
    @DisplayName("수정 시 기본 배송지 행이 없으면 isDefault=false여도 이 배송지가 기본이 된다")
    void updatePointerAbsentBecomesDefault() {
        var target = addressWithId(2L, MEMBER_ID);
        given(shippingAddressRepository.getByIdAndMemberId(2L, MEMBER_ID)).willReturn(target);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        stubDefaultSaveReturningArgument();

        var result = service.update(MEMBER_ID, 2L, request(false));

        var captor = ArgumentCaptor.forClass(DefaultShippingAddress.class);
        then(defaultShippingAddressRepository).should().save(captor.capture());
        assertThat(captor.getValue().isPointingTo(2L)).isTrue();
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("기본 배송지를 변경한다")
    void changeDefault() {
        var current = addressWithId(1L, MEMBER_ID);
        var target = addressWithId(2L, MEMBER_ID);
        var pointer = pointerTo(current);
        given(shippingAddressRepository.getByIdAndMemberId(2L, MEMBER_ID)).willReturn(target);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));
        stubDefaultSaveReturningArgument();

        var result = service.changeDefault(MEMBER_ID, 2L);

        assertThat(pointer.isPointingTo(2L)).isTrue();
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("기본 배송지 행이 빠져 있으면 변경 시 새로 만든다")
    void changeDefaultPointerAbsent() {
        var target = addressWithId(2L, MEMBER_ID);
        given(shippingAddressRepository.getByIdAndMemberId(2L, MEMBER_ID)).willReturn(target);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        stubDefaultSaveReturningArgument();

        var result = service.changeDefault(MEMBER_ID, 2L);

        var captor = ArgumentCaptor.forClass(DefaultShippingAddress.class);
        then(defaultShippingAddressRepository).should().save(captor.capture());
        assertThat(captor.getValue().isPointingTo(2L)).isTrue();
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("기본이 아닌 배송지는 배송지만 지운다")
    void deleteNonDefault() {
        var current = addressWithId(1L, MEMBER_ID);
        var target = addressWithId(2L, MEMBER_ID);
        given(shippingAddressRepository.getByIdAndMemberId(2L, MEMBER_ID)).willReturn(target);
        given(shippingAddressRepository.countByMemberId(MEMBER_ID)).willReturn(2L);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointerTo(current)));

        service.delete(MEMBER_ID, 2L);

        then(shippingAddressRepository).should().delete(target);
        then(defaultShippingAddressRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("배송지가 1개뿐인 기본 배송지는 기본 배송지 행을 먼저 지우고 배송지를 지운다")
    void deleteOnlyDefault() {
        var only = addressWithId(1L, MEMBER_ID);
        var pointer = pointerTo(only);
        given(shippingAddressRepository.getByIdAndMemberId(1L, MEMBER_ID)).willReturn(only);
        given(shippingAddressRepository.countByMemberId(MEMBER_ID)).willReturn(1L);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointer));

        service.delete(MEMBER_ID, 1L);

        var order = inOrder(defaultShippingAddressRepository, shippingAddressRepository);
        order.verify(defaultShippingAddressRepository).delete(pointer);
        order.verify(shippingAddressRepository).delete(only);
    }

    @Test
    @DisplayName("배송지가 여러 개일 때 기본 배송지는 지울 수 없다")
    void deleteDefaultAmongMany() {
        var current = addressWithId(1L, MEMBER_ID);
        given(shippingAddressRepository.getByIdAndMemberId(1L, MEMBER_ID)).willReturn(current);
        given(shippingAddressRepository.countByMemberId(MEMBER_ID)).willReturn(2L);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.of(pointerTo(current)));

        assertThatThrownBy(() -> service.delete(MEMBER_ID, 1L))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.DEFAULT_SHIPPING_ADDRESS_NOT_DELETABLE));
        then(shippingAddressRepository).should(never()).delete(any());
        then(defaultShippingAddressRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("기본 배송지 행이 없으면 배송지만 지운다")
    void deletePointerAbsent() {
        var target = addressWithId(1L, MEMBER_ID);
        given(shippingAddressRepository.getByIdAndMemberId(1L, MEMBER_ID)).willReturn(target);
        given(defaultShippingAddressRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        service.delete(MEMBER_ID, 1L);

        then(shippingAddressRepository).should().delete(target);
        then(defaultShippingAddressRepository).should(never()).delete(any());
    }
}
