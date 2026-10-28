package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.MemberInitialDataService;
import com.bbcc.kidly.boundedcontext.member.app.ShippingAddressService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MemberInitialDataServiceIntegrationTest {

    @Autowired
    private MemberInitialDataService memberInitialDataService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ShippingAddressService shippingAddressService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @DisplayName("회원이 없으면 회원 1~6과 회원 1의 기본 배송지를 넣고, 가입 이벤트로 지갑이 생긴다")
    void load() {
        memberInitialDataService.load();

        var nicknames = LongStream.rangeClosed(1, 6)
            .mapToObj(id -> memberRepository.getById(id).getNickname())
            .toList();
        assertThat(nicknames).containsExactly("테스트보호자", "테스트판매자", "테스트관리자", "정지회원", "신청대기회원", "신청거절회원");
        assertThat(memberRepository.getById(3L).isAdmin()).isTrue();
        assertThat(memberRepository.getById(4L).getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(shippingAddressService.getAddresses(1L)).singleElement().satisfies(address -> {
            assertThat(address.id()).isEqualTo(1L);
            assertThat(address.recipientName()).isEqualTo("홍길동");
            assertThat(address.isDefault()).isTrue();
        });
        assertThat(LongStream.rangeClosed(1, 6)).allMatch(walletRepository::existsByMemberId);
    }

    @Test
    @DisplayName("회원이 한 명이라도 있으면 넣지 않는다")
    void skipWhenMemberExists() {
        memberRepository.save(MemberFixture.kakaoMember());

        memberInitialDataService.load();

        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
