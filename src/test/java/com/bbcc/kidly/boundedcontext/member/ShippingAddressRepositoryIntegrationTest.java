package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.address;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class ShippingAddressRepositoryIntegrationTest {

    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private ShippingAddressRepository shippingAddressRepository;

    @Autowired
    private DefaultShippingAddressRepository defaultShippingAddressRepository;

    @Test
    @DisplayName("회원의 배송지를 등록 순으로 읽고 다른 회원의 배송지는 제외한다")
    void findAllByMemberIdOrderByIdAsc() {
        var first = shippingAddressRepository.save(address());
        shippingAddressRepository.save(address(OTHER_MEMBER_ID));
        var second = shippingAddressRepository.save(address());

        var addresses = shippingAddressRepository.findAllByMemberIdOrderByIdAsc(MEMBER_ID);

        assertThat(addresses).extracting(ShippingAddress::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("회원의 배송지 개수만 센다")
    void countByMemberId() {
        shippingAddressRepository.save(address());
        shippingAddressRepository.save(address());
        shippingAddressRepository.save(address(OTHER_MEMBER_ID));

        assertThat(shippingAddressRepository.countByMemberId(MEMBER_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("본인의 배송지를 조회한다")
    void getByIdAndMemberId() {
        var saved = shippingAddressRepository.save(address());

        assertThat(shippingAddressRepository.getByIdAndMemberId(saved.getId(), MEMBER_ID).getId())
            .isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("다른 회원의 배송지는 없는 것으로 본다")
    void getByIdAndMemberIdOfOtherMember() {
        var saved = shippingAddressRepository.save(address(OTHER_MEMBER_ID));

        assertThatThrownBy(() -> shippingAddressRepository.getByIdAndMemberId(saved.getId(), MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("없는 배송지는 NOT_FOUND다")
    void getByIdAndMemberIdNotFound() {
        assertThatThrownBy(() -> shippingAddressRepository.getByIdAndMemberId(-1L, MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("기본 배송지를 저장하면 회원 ID로 읽고 가리키는 배송지를 알 수 있다")
    void saveDefault() {
        var saved = shippingAddressRepository.save(address());
        defaultShippingAddressRepository.save(DefaultShippingAddress.designate(MEMBER_ID, saved));

        var found = defaultShippingAddressRepository.findById(MEMBER_ID).orElseThrow();

        assertThat(found.getShippingAddress().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("같은 회원의 기본 배송지를 새로 지정해 다시 저장하면 덮어쓰지 않고 실패한다")
    void saveDefaultTwice() {
        var first = shippingAddressRepository.save(address());
        var second = shippingAddressRepository.save(address());
        defaultShippingAddressRepository.save(DefaultShippingAddress.designate(MEMBER_ID, first));

        var duplicate = DefaultShippingAddress.designate(MEMBER_ID, second);

        assertThatThrownBy(() -> defaultShippingAddressRepository.save(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("기본 배송지가 가리키는 배송지는 지울 수 없다")
    void deletePointedAddress() {
        var saved = shippingAddressRepository.save(address());
        defaultShippingAddressRepository.save(DefaultShippingAddress.designate(MEMBER_ID, saved));

        assertThatThrownBy(() -> shippingAddressRepository.delete(saved))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("기본 배송지를 먼저 지우면 배송지를 지울 수 있다")
    void deleteDefaultThenAddress() {
        var saved = shippingAddressRepository.save(address());
        var designated = defaultShippingAddressRepository.save(DefaultShippingAddress.designate(MEMBER_ID, saved));

        defaultShippingAddressRepository.delete(designated);
        shippingAddressRepository.delete(saved);

        assertThat(defaultShippingAddressRepository.findById(MEMBER_ID)).isEmpty();
        assertThat(shippingAddressRepository.countByMemberId(MEMBER_ID)).isZero();
    }

    @Test
    @DisplayName("기본 배송지를 바꿔 저장하면 새 배송지를 가리킨다")
    void changeDefault() {
        var first = shippingAddressRepository.save(address());
        var second = shippingAddressRepository.save(address());
        defaultShippingAddressRepository.save(DefaultShippingAddress.designate(MEMBER_ID, first));

        var found = defaultShippingAddressRepository.findById(MEMBER_ID).orElseThrow();
        found.change(second);
        defaultShippingAddressRepository.save(found);

        var reloaded = defaultShippingAddressRepository.findById(MEMBER_ID).orElseThrow();
        assertThat(reloaded.getShippingAddress().getId()).isEqualTo(second.getId());
    }
}
