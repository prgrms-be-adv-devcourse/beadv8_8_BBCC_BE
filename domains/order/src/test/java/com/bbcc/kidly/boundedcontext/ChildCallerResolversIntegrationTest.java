package com.bbcc.kidly.boundedcontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import com.bbcc.kidly.boundedcontext.cart.in.web.CartCallerArgumentResolver;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildAccountReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChildAccount;
import com.bbcc.kidly.boundedcontext.order.in.web.OrderChild;
import com.bbcc.kidly.boundedcontext.order.in.web.OrderChildCallerArgumentResolver;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import com.bbcc.kidly.boundedcontext.wish.in.web.WishCallerArgumentResolver;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ChildWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 자녀 헤더 해석기 3곳 동작 맞춤(#1222). 장바구니(#998) · 조르기(#906) · 주문 자녀 구매 내역(#1000) 해석기는 컨텍스트별 복사(#617)라
 * 자녀 헤더 규칙이 바뀌면 함께 고쳐야 한다. 같은 자녀 헤더 입력에 세 해석기의 결과(401 · 403과 오류 종류, 성공이면 프로필 · 보호자)가
 * 같은지 본다. 회원 헤더(회원 호출 허용 여부)는 해석기마다 원래 다르므로 비교하지 않는다.
 * 세 컨텍스트를 함께 보는 테스트라 컨텍스트 공용 위치(boundedcontext 루트)에 둔다. 컨텍스트 경계 규칙(OrderContextArchitectureTest)은
 * 운영 코드만 본다(DoNotIncludeTests). 계정 → 프로필은 실제 빈과 DB 계정 복사본으로 찾는다.
 */
@IntegrationTest
class ChildCallerResolversIntegrationTest {

    private static final long GUARDIAN_ID = 7_100L;
    private static final long ACCOUNT_ID = 7_101L;
    private static final long DELETED_ACCOUNT_ID = 7_102L;
    private static final long PROFILE_ID = 71L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private CartActors cartActors;

    @Autowired
    private WishActors wishActors;

    @Autowired
    private ReplicaLookup replicaLookup;

    @Autowired
    private ChildAccountReplicaRepository childAccountReplicaRepository;

    @BeforeEach
    void setUp() {
        childAccountReplicaRepository.save(ReplicaChildAccount.create(
            new ReplicaChildAccount.Values(ACCOUNT_ID, PROFILE_ID, GUARDIAN_ID, false, OCCURRED_AT)));
        childAccountReplicaRepository.save(ReplicaChildAccount.create(
            new ReplicaChildAccount.Values(DELETED_ACCOUNT_ID, 72L, GUARDIAN_ID, true, OCCURRED_AT)));
    }

    @ParameterizedTest(name = "{0} → {3}")
    @MethodSource("childHeaders")
    @DisplayName("같은 자녀 헤더 입력에 세 해석기(장바구니 · 조르기 · 주문 자녀 구매 내역)의 결과가 같다")
    void sameResultForSameChildHeaders(String name, String accountHeader, String guardianHeader, String expected) {
        String cart = outcome(() -> {
            CartActors.Actor actor = new CartCallerArgumentResolver(cartActors)
                .resolveArgument(null, null, request(accountHeader, guardianHeader), null);
            assertThat(actor.type()).as("장바구니 주인은 자녀").isEqualTo(OwnerType.CHILD);
            return ok(actor.id(), actor.memberId());
        });
        String wish = outcome(() -> {
            ChildWishActor actor = (ChildWishActor) new WishCallerArgumentResolver(wishActors)
                .resolveArgument(null, null, request(accountHeader, guardianHeader), null);
            return ok(actor.childProfileId(), actor.guardianId());
        });
        String order = outcome(() -> {
            OrderChild child = new OrderChildCallerArgumentResolver(replicaLookup)
                .resolveArgument(null, null, request(accountHeader, guardianHeader), null);
            return ok(child.childProfileId(), child.guardianId());
        });

        assertThat(cart).as("장바구니").isEqualTo(expected);
        assertThat(wish).as("조르기").isEqualTo(expected);
        assertThat(order).as("주문 자녀 구매 내역").isEqualTo(expected);
    }

    static Stream<Arguments> childHeaders() {
        String account = String.valueOf(ACCOUNT_ID);
        String guardian = String.valueOf(GUARDIAN_ID);
        String unauthorized = "401 UNAUTHORIZED";
        String forbidden = "403 CHILD_ACCOUNT_FORBIDDEN";
        return Stream.of(
            Arguments.of("자녀 헤더 없음", null, null, unauthorized),
            Arguments.of("계정 형식 틀림", "abc", guardian, unauthorized),
            Arguments.of("보호자 형식 틀림", account, "7a", unauthorized),
            Arguments.of("계정 0", "0", guardian, unauthorized),
            Arguments.of("보호자 0", account, "0", unauthorized),
            Arguments.of("계정 음수", "-1", guardian, unauthorized),
            Arguments.of("계정 Long 범위 밖", "9223372036854775808", guardian, unauthorized),
            Arguments.of("보호자 Long 범위 밖", account, "9223372036854775808", unauthorized),
            Arguments.of("계정 헤더만", account, null, unauthorized),
            Arguments.of("보호자 헤더만", null, guardian, unauthorized),
            Arguments.of("받은 적 없는 계정", "7199", guardian, forbidden),
            Arguments.of("삭제된 자녀 계정", String.valueOf(DELETED_ACCOUNT_ID), guardian, forbidden),
            Arguments.of("보호자 다름", account, "7109", forbidden),
            Arguments.of("정상", account, guardian, ok(PROFILE_ID, GUARDIAN_ID)));
    }

    private static NativeWebRequest request(String accountHeader, String guardianHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (accountHeader != null) {
            request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, accountHeader);
        }
        if (guardianHeader != null) {
            request.addHeader(PrincipalHeaders.CHILD_GUARDIAN_ID, guardianHeader);
        }
        return new ServletWebRequest(request);
    }

    private static String ok(Long profileId, Long guardianId) {
        return "OK profile=" + profileId + " guardian=" + guardianId;
    }

    // 성공이면 프로필 · 보호자, 실패면 HTTP 상태와 오류 종류(enum 이름: 공통 401 · 각 컨텍스트의 자녀 계정 403)
    private static String outcome(Resolve resolve) {
        try {
            return resolve.get();
        } catch (BusinessException e) {
            return e.getErrorCode().status().value() + " " + ((Enum<?>) e.getErrorCode()).name();
        }
    }

    @FunctionalInterface
    private interface Resolve {
        String get();
    }
}
