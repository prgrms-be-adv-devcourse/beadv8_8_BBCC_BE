package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.ShippingAddressResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ShippingAddressApiTest {

    private static final String ADDRESSES_PATH = "/api/v1/members/me/addresses";
    private static final String ADDRESS_PATH = "/api/v1/members/me/addresses/{addressId}";
    private static final String DEFAULT_PATH = "/api/v1/members/me/addresses/{addressId}/default";
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("배송지를 등록하면 201과 Location, 등록된 배송지를 주고, 첫 배송지는 기본 배송지다")
    void register() {
        var result = client.post().uri(ADDRESSES_PATH)
            .headers(TestMembers.buyer(MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request("홍길동", false))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ShippingAddressResponse.class);

        var address = result.getResponseBody();
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION))
            .isEqualTo(ADDRESSES_PATH + "/" + address.id());
        assertThat(address.recipientName()).isEqualTo("홍길동");
        assertThat(address.recipientPhone()).isEqualTo("010-1234-5678");
        assertThat(address.zipCode()).isEqualTo("06236");
        assertThat(address.roadAddress()).isEqualTo("서울특별시 강남구 테헤란로 1");
        assertThat(address.detailAddress()).isEqualTo("101호");
        assertThat(address.isDefault()).isTrue();
    }

    @Test
    @DisplayName("응답 JSON의 기본 배송지 키는 isDefault다")
    void isDefaultJsonKey() {
        client.post().uri(ADDRESSES_PATH)
            .headers(TestMembers.buyer(MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request("홍길동", false))
            .exchange()
            .expectStatus().isCreated()
            .expectBody().jsonPath("$.isDefault").isEqualTo(true);
    }

    @Test
    @DisplayName("두 번째 배송지는 기본이 아니고, isDefault true로 등록하면 기본이 바뀐다")
    void registerSecondAndSwitchDefault() {
        var first = registerAddress(MEMBER_ID, "첫째", false);
        var second = registerAddress(MEMBER_ID, "둘째", false);

        assertThat(second.isDefault()).isFalse();
        assertThat(list(MEMBER_ID)).extracting(ShippingAddressResponse::id).containsExactly(first.id(), second.id());

        var third = registerAddress(MEMBER_ID, "셋째", true);

        assertThat(third.isDefault()).isTrue();
        var addresses = list(MEMBER_ID);
        assertThat(addresses).extracting(ShippingAddressResponse::id)
            .containsExactly(third.id(), first.id(), second.id());
        assertThat(addresses).extracting(ShippingAddressResponse::isDefault).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("목록은 기본 배송지가 먼저, 나머지는 등록 순이다")
    void listOrder() {
        var first = registerAddress(MEMBER_ID, "첫째", false);
        var second = registerAddress(MEMBER_ID, "둘째", false);
        var third = registerAddress(MEMBER_ID, "셋째", false);
        requestChangeDefault(MEMBER_ID, third.id());

        assertThat(list(MEMBER_ID)).extracting(ShippingAddressResponse::id)
            .containsExactly(third.id(), first.id(), second.id());
    }

    @Test
    @DisplayName("배송지가 없으면 빈 목록이고, 남의 배송지는 보이지 않는다")
    void listEmptyAndOtherMembers() {
        assertThat(list(MEMBER_ID)).isEmpty();

        registerAddress(OTHER_MEMBER_ID, "타인", false);

        assertThat(list(MEMBER_ID)).isEmpty();
        assertThat(list(OTHER_MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("수정하면 모든 값이 바뀌고, 빈 상세 주소는 null이 된다")
    void update() {
        var address = registerAddress(MEMBER_ID, "홍길동", false);
        var changed = new ShippingAddressRequest(
            "김철수", "010-9999-8888", "04524", "서울특별시 중구 세종대로 110", " ", false);

        var updated = client.patch().uri(ADDRESS_PATH, address.id())
            .headers(TestMembers.buyer(MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(changed)
            .exchange()
            .expectStatus().isOk()
            .returnResult(ShippingAddressResponse.class)
            .getResponseBody();

        assertThat(updated.id()).isEqualTo(address.id());
        assertThat(updated.recipientName()).isEqualTo("김철수");
        assertThat(updated.recipientPhone()).isEqualTo("010-9999-8888");
        assertThat(updated.zipCode()).isEqualTo("04524");
        assertThat(updated.roadAddress()).isEqualTo("서울특별시 중구 세종대로 110");
        assertThat(updated.detailAddress()).isNull();
        assertThat(updated.isDefault()).isTrue();
    }

    @Test
    @DisplayName("수정할 때 isDefault가 true면 기본 배송지가 되고, false는 기본을 바꾸지 않는다")
    void updateWithDefault() {
        var first = registerAddress(MEMBER_ID, "첫째", false);
        var second = registerAddress(MEMBER_ID, "둘째", false);

        var keep = updateAddress(MEMBER_ID, second.id(), request("둘째", false));
        assertThat(keep.isDefault()).isFalse();
        assertThat(list(MEMBER_ID).getFirst().id()).isEqualTo(first.id());

        var switched = updateAddress(MEMBER_ID, second.id(), request("둘째", true));
        assertThat(switched.isDefault()).isTrue();
        assertThat(list(MEMBER_ID).getFirst().id()).isEqualTo(second.id());
    }

    @Test
    @DisplayName("수정할 때 받는 사람 이름이 비어 있으면 400이다")
    void updateWithInvalidInput() {
        var address = registerAddress(MEMBER_ID, "홍길동", false);

        client.patch().uri(ADDRESS_PATH, address.id())
            .headers(TestMembers.buyer(MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request(" ", false))
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("잘못된 요청입니다");
    }

    @Test
    @DisplayName("기본 배송지로 바꾸면 200과 isDefault true를 주고, 목록의 기본이 바뀐다")
    void changeDefault() {
        var first = registerAddress(MEMBER_ID, "첫째", false);
        var second = registerAddress(MEMBER_ID, "둘째", false);

        var changed = client.patch().uri(DEFAULT_PATH, second.id())
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isOk()
            .returnResult(ShippingAddressResponse.class)
            .getResponseBody();

        assertThat(changed.id()).isEqualTo(second.id());
        assertThat(changed.isDefault()).isTrue();
        assertThat(list(MEMBER_ID)).extracting(ShippingAddressResponse::id)
            .containsExactly(second.id(), first.id());
    }

    @Test
    @DisplayName("기본이 아닌 배송지를 삭제하면 204다")
    void deleteNonDefault() {
        registerAddress(MEMBER_ID, "첫째", false);
        var second = registerAddress(MEMBER_ID, "둘째", false);

        client.delete().uri(ADDRESS_PATH, second.id())
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNoContent();

        assertThat(list(MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("배송지가 둘 이상일 때 기본 배송지를 삭제하면 409다")
    void deleteDefaultAmongMany() {
        var first = registerAddress(MEMBER_ID, "첫째", false);
        registerAddress(MEMBER_ID, "둘째", false);

        client.delete().uri(ADDRESS_PATH, first.id())
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("기본 배송지는 삭제할 수 없습니다");

        assertThat(list(MEMBER_ID)).hasSize(2);
    }

    @Test
    @DisplayName("하나뿐인 기본 배송지는 삭제할 수 있고, 다시 등록하면 기본 배송지가 된다")
    void deleteOnlyAddress() {
        var only = registerAddress(MEMBER_ID, "첫째", false);

        client.delete().uri(ADDRESS_PATH, only.id())
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNoContent();

        assertThat(list(MEMBER_ID)).isEmpty();
        assertThat(registerAddress(MEMBER_ID, "새 배송지", false).isDefault()).isTrue();
    }

    @Test
    @DisplayName("남의 배송지는 수정 · 삭제 · 기본 변경 모두 404다")
    void otherMembersAddress() {
        var address = registerAddress(MEMBER_ID, "홍길동", false);

        expectNotFound(OTHER_MEMBER_ID, address.id());
    }

    @Test
    @DisplayName("없는 배송지는 수정 · 삭제 · 기본 변경 모두 404다")
    void missingAddress() {
        expectNotFound(MEMBER_ID, MISSING_ID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 입력이면 400이다")
    void invalidInput(String name, ShippingAddressRequest request, String field) {
        var body = client.post().uri(ADDRESSES_PATH)
            .headers(TestMembers.buyer(MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("잘못된 요청입니다")
            .returnResult()
            .getResponseBody();

        assertThat(new String(body, StandardCharsets.UTF_8)).contains(field);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of("받는 사람 이름이 비어 있음",
                new ShippingAddressRequest(" ", "010-1234-5678", "06236", "도로명", null, false), "recipientName"),
            Arguments.of("받는 사람 이름이 51자",
                new ShippingAddressRequest("가".repeat(51), "010-1234-5678", "06236", "도로명", null, false),
                "recipientName"),
            Arguments.of("전화번호 형식이 잘못됨",
                new ShippingAddressRequest("홍길동", "01012345678", "06236", "도로명", null, false), "recipientPhone"),
            Arguments.of("전화번호가 null",
                new ShippingAddressRequest("홍길동", null, "06236", "도로명", null, false), "recipientPhone"),
            Arguments.of("우편번호가 비어 있음",
                new ShippingAddressRequest("홍길동", "010-1234-5678", " ", "도로명", null, false), "zipCode"),
            Arguments.of("도로명 주소가 비어 있음",
                new ShippingAddressRequest("홍길동", "010-1234-5678", "06236", " ", null, false), "roadAddress"),
            Arguments.of("상세 주소가 101자",
                new ShippingAddressRequest("홍길동", "010-1234-5678", "06236", "도로명", "가".repeat(101), false),
                "detailAddress"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        client.get().uri(ADDRESSES_PATH)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private ShippingAddressRequest request(String recipientName, boolean isDefault) {
        return new ShippingAddressRequest(
            recipientName, "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 1", "101호", isDefault);
    }

    private ShippingAddressResponse registerAddress(Long memberId, String recipientName, boolean isDefault) {
        return client.post().uri(ADDRESSES_PATH)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request(recipientName, isDefault))
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ShippingAddressResponse.class)
            .getResponseBody();
    }

    private List<ShippingAddressResponse> list(Long memberId) {
        return client.get().uri(ADDRESSES_PATH)
            .headers(TestMembers.buyer(memberId))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<ShippingAddressResponse>>() { })
            .returnResult()
            .getResponseBody();
    }

    private ShippingAddressResponse updateAddress(Long memberId, Long addressId, ShippingAddressRequest request) {
        return client.patch().uri(ADDRESS_PATH, addressId)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isOk()
            .returnResult(ShippingAddressResponse.class)
            .getResponseBody();
    }

    private void requestChangeDefault(Long memberId, Long addressId) {
        client.patch().uri(DEFAULT_PATH, addressId)
            .headers(TestMembers.buyer(memberId))
            .exchange()
            .expectStatus().isOk();
    }

    private void expectNotFound(Long memberId, Long addressId) {
        var login = TestMembers.buyer(memberId);
        client.patch().uri(ADDRESS_PATH, addressId)
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request("홍길동", false))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        client.delete().uri(ADDRESS_PATH, addressId)
            .headers(login)
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        client.patch().uri(DEFAULT_PATH, addressId)
            .headers(login)
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
