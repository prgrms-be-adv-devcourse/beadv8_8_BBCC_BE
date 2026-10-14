package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.ShippingAddressFixture.MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddress;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressInfo;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShippingAddressTest {

    // fixture 값에서 한 필드만 바꾼 입력을 만든다
    private static ShippingAddressInfo withName(String v) {
        var base = ShippingAddressFixture.info();
        return new ShippingAddressInfo(v, base.recipientPhone(), base.zipCode(), base.roadAddress(),
            base.detailAddress());
    }

    private static ShippingAddressInfo withPhone(String v) {
        var base = ShippingAddressFixture.info();
        return new ShippingAddressInfo(base.recipientName(), v, base.zipCode(), base.roadAddress(),
            base.detailAddress());
    }

    private static ShippingAddressInfo withZip(String v) {
        var base = ShippingAddressFixture.info();
        return new ShippingAddressInfo(base.recipientName(), base.recipientPhone(), v, base.roadAddress(),
            base.detailAddress());
    }

    private static ShippingAddressInfo withRoad(String v) {
        var base = ShippingAddressFixture.info();
        return new ShippingAddressInfo(base.recipientName(), base.recipientPhone(), base.zipCode(), v,
            base.detailAddress());
    }

    private static ShippingAddressInfo withDetail(String v) {
        var base = ShippingAddressFixture.info();
        return new ShippingAddressInfo(base.recipientName(), base.recipientPhone(), base.zipCode(),
            base.roadAddress(), v);
    }

    static Stream<Arguments> nullRequired() {
        return Stream.of(
            Arguments.of("recipientName", withName(null)),
            Arguments.of("recipientPhone", withPhone(null)),
            Arguments.of("zipCode", withZip(null)),
            Arguments.of("roadAddress", withRoad(null)));
    }

    static Stream<Arguments> blankRequired() {
        return Stream.of("", " ").flatMap(blank -> Stream.of(
            Arguments.of("recipientName", withName(blank)),
            Arguments.of("recipientPhone", withPhone(blank)),
            Arguments.of("zipCode", withZip(blank)),
            Arguments.of("roadAddress", withRoad(blank))));
    }

    static Stream<Arguments> tooLong() {
        return Stream.of(
            Arguments.of("recipientName 51자", withName("가".repeat(51))),
            Arguments.of("zipCode 11자", withZip("1".repeat(11))),
            Arguments.of("roadAddress 201자", withRoad("가".repeat(201))),
            Arguments.of("detailAddress 101자", withDetail("가".repeat(101))));
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

    @Test
    @DisplayName("상세 주소는 null이어도 등록된다")
    void detailAddressMayBeNull() {
        var address = ShippingAddress.register(MEMBER_ID, withDetail(null));

        assertThat(address.getDetailAddress()).isNull();
    }

    @Test
    @DisplayName("회원 id가 null이면 등록할 수 없다")
    void registerRejectsNullMemberId() {
        assertThatThrownBy(() -> ShippingAddress.register(null, ShippingAddressFixture.info()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("info가 null이면 등록할 수 없다")
    void registerRejectsNullInfo() {
        assertThatThrownBy(() -> ShippingAddress.register(MEMBER_ID, null))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "{0}이(가) null이면 등록할 수 없다")
    @MethodSource("nullRequired")
    @DisplayName("필수 값이 null이면 등록할 수 없다")
    void registerRejectsNullRequired(String field, ShippingAddressInfo info) {
        assertThatThrownBy(() -> ShippingAddress.register(MEMBER_ID, info))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "{0}이(가) 비어 있으면 등록할 수 없다")
    @MethodSource("blankRequired")
    @DisplayName("필수 값이 비어 있으면 등록할 수 없다")
    void registerRejectsBlankRequired(String field, ShippingAddressInfo info) {
        assertThatThrownBy(() -> ShippingAddress.register(MEMBER_ID, info))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}이면 등록할 수 없다")
    @MethodSource("tooLong")
    @DisplayName("길이 상한을 넘으면 등록할 수 없다")
    void registerRejectsTooLong(String field, ShippingAddressInfo info) {
        assertThatThrownBy(() -> ShippingAddress.register(MEMBER_ID, info))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("길이 상한과 같으면 등록할 수 있다")
    void registerAcceptsMaxLength() {
        var info = new ShippingAddressInfo(
            "가".repeat(50), "010-1234-5678", "1".repeat(10), "가".repeat(200), "가".repeat(100));

        var address = ShippingAddress.register(MEMBER_ID, info);

        assertThat(address.getRecipientName()).hasSize(50);
        assertThat(address.getZipCode()).hasSize(10);
        assertThat(address.getRoadAddress()).hasSize(200);
        assertThat(address.getDetailAddress()).hasSize(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"010-1234-5678", "02-123-4567", "031-123-4567"})
    @DisplayName("연락처가 형식에 맞으면 등록할 수 있다")
    void registerAcceptsValidPhone(String phone) {
        var address = ShippingAddress.register(MEMBER_ID, withPhone(phone));

        assertThat(address.getRecipientPhone()).isEqualTo(phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "010-12-5678", "010-1234-567a", "1010-1234-5678"})
    @DisplayName("연락처가 형식에 맞지 않으면 등록할 수 없다")
    void registerRejectsInvalidPhone(String phone) {
        assertThatThrownBy(() -> ShippingAddress.register(MEMBER_ID, withPhone(phone)))
            .isInstanceOf(IllegalArgumentException.class);
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

    @Test
    @DisplayName("수정이 검증에 실패하면 이전 값이 그대로 남는다")
    void updateKeepsValuesOnFailure() {
        var address = ShippingAddressFixture.address();
        var invalid = new ShippingAddressInfo(
            "김철수", "02-123-4567", "12345", "부산 해운대구 해운대로 1", "가".repeat(101));

        assertThatThrownBy(() -> address.update(invalid)).isInstanceOf(IllegalArgumentException.class);

        assertThat(address.getRecipientName()).isEqualTo("홍길동");
        assertThat(address.getRecipientPhone()).isEqualTo("010-1234-5678");
        assertThat(address.getZipCode()).isEqualTo("06236");
        assertThat(address.getRoadAddress()).isEqualTo("서울 강남구 테헤란로 212");
        assertThat(address.getDetailAddress()).isEqualTo("101동 1001호");
    }

    @Test
    @DisplayName("같은 회원의 배송지만 본인 소유로 판단한다")
    void isOwnedBy() {
        var address = ShippingAddressFixture.address(MEMBER_ID);

        assertThat(address.isOwnedBy(MEMBER_ID)).isTrue();
        assertThat(address.isOwnedBy(2L)).isFalse();
    }
}
