package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.MemberFixture;
import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@IntegrationTest
class SellerSuspensionIntegrationTest {

    @Autowired
    private SellerApplicationService sellerApplicationService;

    @Autowired
    private SellerService sellerService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    // 트랜잭션 없이 호출해야 커밋 뒤에 실행되는 회원 리스너가 돈다
    @Test
    @DisplayName("정지하면 회원이 판매자 자격을 잃어 새로 발급한 토큰에 SELLER 역할이 없다")
    void suspend() throws InterruptedException {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        var request = new SellerApplicationRequest(SellerFixture.BUSINESS_NAME, SellerFixture.REPRESENTATIVE_NAME,
            SellerFixture.BUSINESS_REGISTRATION_NUMBER);
        var application = sellerApplicationService.submit(member.getId(), member.getNickname(), request);
        sellerApplicationService.approve(application.id(), SellerFixture.ADMIN_ID);

        assertThat(memberRepository.findById(member.getId()).orElseThrow().isActiveSeller()).isTrue();

        sellerService.suspend(member.getId(), SellerFixture.ADMIN_ID, new SellerSuspendRequest("허위 상품 등록 신고"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().isActiveSeller()).isFalse();
        // 무효화와 같은 초에 발급한 토큰은 무효화 기준에 걸리므로 다음 초까지 기다린다
        Thread.sleep(Duration.ofSeconds(1));
        var jwt = jwtDecoder.decode(authTokenService.issueAccessToken(memberRepository.getById(member.getId())));
        assertThat(jwt.getClaimAsStringList("roles")).doesNotContain("SELLER");
    }
}
