package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationCache;
import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.DefaultShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.ShippingAddressRepository;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class MemberServiceIntegrationTest {

    private static final SocialIdentity KAKAO_1 = new SocialIdentity(OAuthProvider.KAKAO, "kakao-1");

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberRegistrar memberRegistrar;

    @Autowired
    private ShippingAddressService shippingAddressService;

    @Autowired
    private ShippingAddressRepository shippingAddressRepository;

    @Autowired
    private DefaultShippingAddressRepository defaultShippingAddressRepository;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private AccessTokenRevocationCache accessTokenRevocationCache;

    @Test
    @DisplayName("탈퇴하면 소셜 계정을 지우고 탈퇴 이벤트를 발행하며, 이벤트를 받아 배송지 · 기본 배송지를 지운다")
    void withdraw() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        shippingAddressService.register(memberId, addressRequest(true));
        shippingAddressService.register(memberId, addressRequest(false));

        memberService.withdraw(memberId);

        assertThat(memberRepository.findById(memberId)).hasValueSatisfying(member -> {
            assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
            assertThat(member.getWithdrawnAt()).isNotNull();
            assertThat(member.getNickname()).isNull();
            assertThat(member.getEmail()).isNull();
        });
        assertThat(memberRepository.findBySocialAccount(KAKAO_1)).isEmpty();
        assertThat(shippingAddressRepository.countByMemberId(memberId)).isZero();
        assertThat(defaultShippingAddressRepository.findById(memberId)).isEmpty();
        assertThat(events.stream(MemberWithdrawnEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.data().memberId()).isEqualTo(memberId);
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("탈퇴하면 이미 발급된 Access Token을 바로 무효화한다")
    void withdrawRevokesAccessToken() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        memberService.withdraw(memberId);

        assertThat(accessTokenRevocationCache.isRevoked(memberId, Instant.now().minusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 회원의 소셜 계정으로 다시 가입하면 새 회원이 된다")
    void registerAgainAfterWithdraw() {
        var withdrawnId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        memberService.withdraw(withdrawnId);

        var member = memberRegistrar.register(
            new MemberRegisterInfo(KAKAO_1, new Nickname(MemberFixture.NICKNAME), MemberFixture.EMAIL));

        assertThat(member.getId()).isNotEqualTo(withdrawnId);
        assertThat(memberRepository.findBySocialAccount(KAKAO_1))
            .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(member.getId()));
    }

    @Test
    @DisplayName("관리자는 탈퇴할 수 없고 이벤트를 발행하지 않는다")
    void adminCannotWithdraw() {
        var memberId = memberRepository.save(MemberFixture.withRole(MemberRole.ADMIN)).getId();

        assertThatThrownBy(() -> memberService.withdraw(memberId))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_WITHDRAWABLE));
        assertThat(memberRepository.findById(memberId))
            .hasValueSatisfying(member -> assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE));
        assertThat(events.stream(MemberWithdrawnEvent.class)).isEmpty();
    }

    private ShippingAddressRequest addressRequest(boolean isDefault) {
        var info = ShippingAddressFixture.info();
        return new ShippingAddressRequest(info.recipientName(), info.recipientPhone(), info.zipCode(),
            info.roadAddress(), info.detailAddress(), isDefault);
    }
}
