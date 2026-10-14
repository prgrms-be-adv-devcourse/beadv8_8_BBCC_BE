package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class MemberSummaryQueryIntegrationTest {

    private static final String REASON = "욕설이 담긴 리뷰로 신고가 접수되었어요.";

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberSummaryQuery query;

    private Member buyer;
    private Member seller;
    private Member suspendedSeller;
    private Member suspendedMember;

    // 5 · 6번(관리자 · 탈퇴)은 목록에서 빠져야 하는 회원
    @BeforeEach
    void setUp() {
        buyer = memberRepository.save(MemberFixture.kakaoMember("1"));
        seller = memberRepository.save(MemberFixture.withSellerState("2", SellerStatus.ACTIVE));
        suspendedSeller = memberRepository.save(MemberFixture.withSellerState("3", SellerStatus.SUSPENDED));
        suspendedMember = MemberFixture.kakaoMember("4");
        suspendedMember.suspend(100L, REASON, MemberFixture.NOW);
        memberRepository.save(suspendedMember);
        memberRepository.save(MemberFixture.withRole("5", MemberRole.ADMIN));
        memberRepository.save(MemberFixture.withStatus("6", MemberStatus.WITHDRAWN));
    }

    @Test
    @DisplayName("ALL이면 관리자 · 탈퇴 회원을 빼고 모두 읽는다")
    void findAll() {
        var page = query.findAll(AdminMemberFilter.ALL, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(buyer.getId(), seller.getId(), suspendedSeller.getId(), suspendedMember.getId());
    }

    @Test
    @DisplayName("BUYER는 판매자 이력이 없는 회원이다. 이용 정지된 회원도 들어간다")
    void findBuyers() {
        var page = query.findAll(AdminMemberFilter.BUYER, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(buyer.getId(), suspendedMember.getId());
    }

    @Test
    @DisplayName("SELLER는 판매자 이력이 있는 회원이다. 판매자 정지도 들어간다")
    void findSellers() {
        var page = query.findAll(AdminMemberFilter.SELLER, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(seller.getId(), suspendedSeller.getId());
    }

    @Test
    @DisplayName("SUSPENDED는 이용 정지된 회원만이고 정지 사유 · 시각을 준다")
    void findSuspended() {
        var page = query.findAll(AdminMemberFilter.SUSPENDED, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(suspendedMember.getId());
            assertThat(row.status()).isEqualTo(MemberStatus.SUSPENDED.name());
            assertThat(row.suspendReason()).isEqualTo(REASON);
            assertThat(row.suspendedAt()).isEqualTo(MemberFixture.NOW);
        });
    }

    @Test
    @DisplayName("판매자 정지 회원의 행은 sellerStatus가 SUSPENDED이고 정지 사유는 없다")
    void suspendedSellerRow() {
        var page = query.findAll(AdminMemberFilter.SELLER, PageRequest.of(0, 10));

        var row = page.getContent().stream()
            .filter(summary -> summary.id().equals(suspendedSeller.getId())).findFirst().orElseThrow();
        assertThat(row.status()).isEqualTo(MemberStatus.ACTIVE.name());
        assertThat(row.sellerStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(row.suspendReason()).isNull();
        assertThat(row.nickname()).isEqualTo(suspendedSeller.getNickname());
        assertThat(row.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("최신 가입(id 내림차순)부터 읽는다")
    void findAllNewestFirst() {
        var page = query.findAll(AdminMemberFilter.ALL, PageRequest.of(0, 10));

        var ids = page.getContent().stream().map(AdminMemberSummary::id).toList();
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("페이지 크기만큼 읽고 전체 개수와 다음 페이지 여부를 알려준다")
    void findAllPaged() {
        var page = query.findAll(AdminMemberFilter.ALL, PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.hasNext()).isTrue();
    }
}
