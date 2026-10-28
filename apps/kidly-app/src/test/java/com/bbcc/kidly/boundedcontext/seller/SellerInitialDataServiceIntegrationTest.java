package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.MemberInitialDataService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerInitialDataService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerResult;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SellerInitialDataServiceIntegrationTest {

    @Autowired
    private MemberInitialDataService memberInitialDataService;

    @Autowired
    private SellerInitialDataService sellerInitialDataService;

    @Autowired
    private SellerService sellerService;

    @Autowired
    private SellerApplicationService sellerApplicationService;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 승인 이벤트로 회원 2가 판매자가 되는지 보려고 회원 초기 데이터를 먼저 넣는다
    @Test
    @DisplayName("회원 2를 판매자로 승인하고, 회원 5는 대기 · 회원 6은 거절 신청을 둔다")
    void load() {
        memberInitialDataService.load();

        sellerInitialDataService.load();

        assertThat(sellerService.getMine(2L)).isEqualTo(new SellerResult(2L, "테스트상점", SellerStatus.ACTIVE));
        assertThat(memberRepository.getById(2L).isActiveSeller()).isTrue();
        assertThat(sellerApplicationService.getLatest(5L).status()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(sellerApplicationService.getLatest(6L).status()).isEqualTo(SellerApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("판매자 신청이 하나라도 있으면 넣지 않는다")
    void skipWhenApplicationExists() {
        memberInitialDataService.load();
        sellerApplicationService.submit(5L, "신청대기회원", new SellerApplicationRequest("기존상점", "홍길동", "1000000005"));

        sellerInitialDataService.load();

        assertThat(sellerApplicationRepository.count()).isEqualTo(1);
    }
}
