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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
    private Member admin;
    private Member withdrawn;

    // 6번(탈퇴)은 어느 필터에도 나오지 않고, 5번(관리자)은 ADMIN에서만 나온다
    @BeforeEach
    void setUp() {
        buyer = memberRepository.save(MemberFixture.kakaoMember("1"));
        seller = memberRepository.save(MemberFixture.withSellerState("2", SellerStatus.ACTIVE));
        suspendedSeller = memberRepository.save(MemberFixture.withSellerState("3", SellerStatus.SUSPENDED));
        suspendedMember = MemberFixture.kakaoMember("4");
        suspendedMember.suspend(100L, REASON, MemberFixture.NOW);
        memberRepository.save(suspendedMember);
        admin = memberRepository.save(MemberFixture.withRole("5", MemberRole.ADMIN));
        withdrawn = memberRepository.save(MemberFixture.withStatus("6", MemberStatus.WITHDRAWN));
    }

    @Test
    @DisplayName("ALL이면 관리자 · 탈퇴 회원을 빼고 모두 읽는다")
    void findAll() {
        var page = query.findAll(AdminMemberFilter.ALL, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(buyer.getId(), seller.getId(), suspendedSeller.getId(), suspendedMember.getId());
    }

    @Test
    @DisplayName("BUYER는 판매자 이력이 없는 회원이다. 이용 정지된 회원도 들어간다")
    void findBuyers() {
        var page = query.findAll(AdminMemberFilter.BUYER, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(buyer.getId(), suspendedMember.getId());
    }

    @Test
    @DisplayName("SELLER는 판매자 이력이 있는 회원이다. 판매자 정지도 들어간다")
    void findSellers() {
        var page = query.findAll(AdminMemberFilter.SELLER, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(seller.getId(), suspendedSeller.getId());
    }

    @Test
    @DisplayName("SUSPENDED는 이용 정지된 회원만이고 정지 사유 · 시각을 준다")
    void findSuspended() {
        var page = query.findAll(AdminMemberFilter.SUSPENDED, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(suspendedMember.getId());
            assertThat(row.status()).isEqualTo(MemberStatus.SUSPENDED.name());
            assertThat(row.suspendReason()).isEqualTo(REASON);
            assertThat(row.suspendedAt()).isEqualTo(MemberFixture.NOW);
        });
    }

    @Test
    @DisplayName("ADMIN은 관리자 회원만 읽는다")
    void findAdmins() {
        var page = query.findAll(AdminMemberFilter.ADMIN, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id).containsExactly(admin.getId());
    }

    @Test
    @DisplayName("ADMIN도 keyword로 검색한다")
    void searchAdmins() {
        var page = query.findAll(AdminMemberFilter.ADMIN, buyer.getNickname(), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(query.findAll(AdminMemberFilter.ADMIN, admin.getNickname(), PageRequest.of(0, 10)).getContent())
            .extracting(AdminMemberSummary::id).containsExactly(admin.getId());
    }

    @Test
    @DisplayName("판매자인 관리자는 SELLER가 아니라 ADMIN에서 나온다")
    void sellerAdminOnlyInAdmin() {
        var sellerAdmin = MemberFixture.withSellerState("7", SellerStatus.ACTIVE);
        sellerAdmin.grantAdmin();
        memberRepository.save(sellerAdmin);

        assertThat(query.findAll(AdminMemberFilter.SELLER, null, PageRequest.of(0, 10)).getContent())
            .extracting(AdminMemberSummary::id).doesNotContain(sellerAdmin.getId());
        assertThat(query.findAll(AdminMemberFilter.ADMIN, null, PageRequest.of(0, 10)).getContent())
            .extracting(AdminMemberSummary::id).containsExactlyInAnyOrder(admin.getId(), sellerAdmin.getId());
    }

    @Test
    @DisplayName("판매자 정지 회원의 행은 sellerStatus가 SUSPENDED이고 정지 사유는 없다")
    void suspendedSellerRow() {
        var page = query.findAll(AdminMemberFilter.SELLER, null, PageRequest.of(0, 10));

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
        var page = query.findAll(AdminMemberFilter.ALL, null, PageRequest.of(0, 10));

        var ids = page.getContent().stream().map(AdminMemberSummary::id).toList();
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("페이지 크기만큼 읽고 전체 개수와 다음 페이지 여부를 알려준다")
    void findAllPaged() {
        var page = query.findAll(AdminMemberFilter.ALL, null, PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("닉네임 일부로 회원을 찾는다")
    void searchByNickname() {
        var page = query.findAll(AdminMemberFilter.ALL, "회원-2", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id).containsExactly(seller.getId());
    }

    // 닉네임에 번호가 없는 회원은 번호로만, 닉네임에 번호가 든 회원은 닉네임으로만 걸린다
    @Test
    @DisplayName("숫자만 있으면 회원 번호가 같은 회원과 닉네임에 그 숫자가 든 회원을 모두 찾는다")
    void searchByIdOrNickname() {
        var byId = memberRepository.save(MemberFixture.withNickname("by-id", "번호없음"));
        var byNickname = memberRepository.save(MemberFixture.withNickname("by-nickname", "번호-" + byId.getId()));

        var page = query.findAll(AdminMemberFilter.ALL, String.valueOf(byId.getId()), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(byId.getId(), byNickname.getId());
    }

    @Test
    @DisplayName("목록 행에 이메일이 있다")
    void rowHasEmail() {
        var page = query.findAll(AdminMemberFilter.BUYER, null, PageRequest.of(0, 10));

        var row = page.getContent().stream()
            .filter(summary -> summary.id().equals(buyer.getId())).findFirst().orElseThrow();
        assertThat(row.email()).isEqualTo(buyer.getEmail());
    }

    @Test
    @DisplayName("이메일 일부로 회원을 찾는다")
    void searchByEmail() {
        var page = query.findAll(AdminMemberFilter.ALL, "2@", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id).containsExactly(seller.getId());
    }

    @Test
    @DisplayName("이메일을 받기 전에 가입한 회원도 목록에 나오고 email은 null이다")
    void listsMemberWithoutEmail() {
        var noEmail = memberRepository.save(MemberFixture.withoutEmail("no-email"));

        var page = query.findAll(AdminMemberFilter.ALL, null, PageRequest.of(0, 10));

        var row = page.getContent().stream()
            .filter(summary -> summary.id().equals(noEmail.getId())).findFirst().orElseThrow();
        assertThat(row.email()).isNull();
    }

    // 이메일이 null이면 이메일 조건만 거짓이 되고 회원 번호 · 닉네임 조건은 그대로 걸린다
    @Test
    @DisplayName("이메일을 받기 전에 가입한 회원도 회원 번호 · 닉네임으로 찾는다")
    void searchMemberWithoutEmail() {
        var noEmail = memberRepository.save(MemberFixture.withoutEmail("no-email"));

        var byId = query.findAll(AdminMemberFilter.ALL, String.valueOf(noEmail.getId()), PageRequest.of(0, 10));
        var byNickname = query.findAll(AdminMemberFilter.ALL, "회원-no-email", PageRequest.of(0, 10));

        assertThat(byId.getContent()).extracting(AdminMemberSummary::id).containsExactly(noEmail.getId());
        assertThat(byNickname.getContent()).extracting(AdminMemberSummary::id).containsExactly(noEmail.getId());
    }

    // 이메일에 번호가 든 회원은 닉네임에 숫자가 없어 이메일로만 걸린다
    @Test
    @DisplayName("숫자만 있으면 회원 번호가 같은 회원과 이메일에 그 숫자가 든 회원을 모두 찾는다")
    void searchByIdOrEmail() {
        var byId = memberRepository.save(MemberFixture.withNickname("by-id", "번호없음"));
        var byEmail = memberRepository.save(MemberFixture.withNickname("mail-" + byId.getId(), "메일회원"));

        var page = query.findAll(AdminMemberFilter.ALL, String.valueOf(byId.getId()), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(byId.getId(), byEmail.getId());
    }

    @Test
    @DisplayName("키워드 앞뒤 공백은 무시한다")
    void searchStripsWhitespace() {
        var page = query.findAll(AdminMemberFilter.ALL, "  회원-2  ", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id).containsExactly(seller.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("키워드가 null · 빈 값 · 공백이면 조건에서 빠진다")
    void searchWithBlankKeyword(String keyword) {
        var page = query.findAll(AdminMemberFilter.ALL, keyword, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id)
            .containsExactlyInAnyOrder(buyer.getId(), seller.getId(), suspendedSeller.getId(), suspendedMember.getId());
    }

    @Test
    @DisplayName("필터와 키워드는 함께 적용된다")
    void searchWithFilter() {
        var page = query.findAll(AdminMemberFilter.SELLER, "회원-2", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AdminMemberSummary::id).containsExactly(seller.getId());

        var buyers = query.findAll(AdminMemberFilter.BUYER, "회원-2", PageRequest.of(0, 10));

        assertThat(buyers.getContent()).isEmpty();
    }

    @Test
    @DisplayName("키워드로 걸러진 전체 개수와 다음 페이지 여부를 알려준다")
    void searchPaged() {
        memberRepository.save(MemberFixture.withNickname("page-1", "검색-가"));
        memberRepository.save(MemberFixture.withNickname("page-2", "검색-나"));

        var page = query.findAll(AdminMemberFilter.ALL, "검색", PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "회원_2"})
    @DisplayName("%와 _는 와일드카드가 아니라 문자 그대로 찾는다")
    void searchTreatsWildcardsLiterally(String keyword) {
        var page = query.findAll(AdminMemberFilter.ALL, keyword, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("ALL에서는 관리자 · 탈퇴 회원을 회원 번호로 찾아도 나오지 않는다")
    void searchExcludesAdminAndWithdrawn() {
        var byAdminId = query.findAll(AdminMemberFilter.ALL, String.valueOf(admin.getId()), PageRequest.of(0, 10));
        var byWithdrawnId = query.findAll(AdminMemberFilter.ALL, String.valueOf(withdrawn.getId()),
            PageRequest.of(0, 10));

        assertThat(byAdminId.getContent()).isEmpty();
        assertThat(byWithdrawnId.getContent()).isEmpty();
    }

    @Test
    @DisplayName("19자리 이상 숫자도 오류 없이 검색한다")
    void searchWithOverflowingNumber() {
        var page = query.findAll(AdminMemberFilter.ALL, "9".repeat(19), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }
}
