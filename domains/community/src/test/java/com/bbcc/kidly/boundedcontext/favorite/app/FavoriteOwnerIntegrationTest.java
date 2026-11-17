package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.favorite.app.port.in.ChildFavoritesRemover;
import com.bbcc.kidly.boundedcontext.favorite.app.port.in.MemberFavoritesRemover;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 찜 소유자: 회원 본인과 자녀 계정은 각자 찜을 갖는다. 회원 7과 자녀 계정 7(보호자 70)은 ID가 같아도 다른 소유자다.
 * 보호자 탈퇴는 본인 + 자녀 계정 찜을, 자녀 계정 삭제는 그 계정 찜만 지우고 그 계정을 막는다.
 */
@IntegrationTest
class FavoriteOwnerIntegrationTest {

    private static final long SAME_ID = 7L;
    private static final long GUARDIAN = 70L;
    private static final FavoriteOwner MEMBER = FavoriteOwner.member(SAME_ID);
    private static final FavoriteOwner CHILD = FavoriteOwner.child(SAME_ID, GUARDIAN);
    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 11, 16, 10, 0, 0, 123_456_123);

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteQueryService queryService;

    @Autowired
    private FavoriteCallerService callerService;

    @Autowired
    private MemberFavoritesRemover memberFavoritesRemover;

    @Autowired
    private ChildFavoritesRemover childFavoritesRemover;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void products() {
        for (long product = 14; product <= 17; product++) {
            jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, "
                + "display_status, source_occurred_at, created_at, updated_at) values (?, '상품', null, 'ON_SALE', "
                + "now(), now(), now())", product);
        }
    }

    @Test
    @DisplayName("회원 7과 자녀 계정 7의 찜은 목록 · productIds · 해제가 서로 섞이지 않고, 찜 수는 합계다")
    void sameIdDoesNotMix() {
        favoriteService.add(MEMBER, "14");
        favoriteService.add(MEMBER, "15");
        favoriteService.add(CHILD, "14");
        favoriteService.add(CHILD, "16");

        assertThat(mine(MEMBER)).containsExactly(15L, 14L);
        assertThat(mine(CHILD)).containsExactly(16L, 14L);
        assertThat(queryService.findMine(MEMBER, "14,15,16", null, null).items())
            .extracting(FavoriteQueryService.FavoriteItem::productId).containsExactly(15L, 14L);
        assertThat(queryService.findMine(CHILD, "14,15,16", null, null).items())
            .extracting(FavoriteQueryService.FavoriteItem::productId).containsExactly(16L, 14L);
        assertThat(queryService.summary("14").favoriteCount()).isEqualTo(2);

        favoriteService.remove(CHILD, "14");
        favoriteService.remove(MEMBER, "16");

        assertThat(mine(MEMBER)).containsExactly(15L, 14L);
        assertThat(mine(CHILD)).containsExactly(16L);
        assertThat(queryService.summary("14").favoriteCount()).isEqualTo(1);
        assertThat(queryService.summary("16").favoriteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("자녀 계정 찜 행은 owner_type CHILD · owner_id 계정 ID · guardian_id = member_id = 보호자 ID, 회원 찜은 셋 다 본인")
    void storesOwnerColumns() {
        favoriteService.add(CHILD, "14");
        favoriteService.add(MEMBER, "14");

        assertThat(jdbcTemplate.queryForList("select owner_type || ':' || owner_id || ':' || guardian_id || ':' "
            + "|| member_id from community.favorite order by id", String.class))
            .containsExactly("CHILD:7:70:70", "MEMBER:7:7:7");
    }

    @Test
    @DisplayName("같은 자녀 계정이 같은 상품을 두 번 찜해도 1행 · 찜 수 1")
    void childAddIdempotent() {
        favoriteService.add(CHILD, "14");
        favoriteService.add(CHILD, "14");

        assertThat(mine(CHILD)).containsExactly(14L);
        assertThat(queryService.summary("14").favoriteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("보호자 탈퇴는 보호자 본인 찜과 그 보호자의 자녀 계정 찜을 지우고, ID가 같은 다른 회원(7)의 찜은 그대로다")
    void guardianWithdrawalRemovesChildFavorites() {
        FavoriteOwner guardian = FavoriteOwner.member(GUARDIAN);
        favoriteService.add(guardian, "14");
        favoriteService.add(CHILD, "14");
        favoriteService.add(CHILD, "15");
        favoriteService.add(MEMBER, "14");

        assertThat(memberFavoritesRemover.removeAll(GUARDIAN)).isEqualTo(3);

        assertThat(mine(guardian)).isEmpty();
        assertThat(mine(CHILD)).isEmpty();
        assertThat(mine(MEMBER)).containsExactly(14L);
        assertThat(queryService.summary("14").favoriteCount()).isEqualTo(1);
        assertThat(queryService.summary("15").favoriteCount()).isZero();
    }

    @Test
    @DisplayName("자녀 계정 삭제는 그 계정 찜만 지우고(보호자 · 회원 7 찜은 그대로) 계정을 남겨 그 뒤 요청을 403으로 막는다. 두 번째는 변화 없음")
    void childDeletionRemovesOnlyChild() {
        favoriteService.add(FavoriteOwner.member(GUARDIAN), "14");
        favoriteService.add(CHILD, "14");
        favoriteService.add(CHILD, "15");
        favoriteService.add(MEMBER, "15");

        assertThat(childFavoritesRemover.removeAll(SAME_ID, GUARDIAN, DELETED_AT)).isEqualTo(2);
        assertThat(childFavoritesRemover.removeAll(SAME_ID, GUARDIAN, DELETED_AT)).isZero();

        assertThat(mine(CHILD)).isEmpty();
        assertThat(mine(FavoriteOwner.member(GUARDIAN))).containsExactly(14L);
        assertThat(mine(MEMBER)).containsExactly(15L);
        assertThat(queryService.summary("14").favoriteCount()).isEqualTo(1);
        assertThat(queryService.summary("15").favoriteCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("select guardian_id, deleted_at::text as deleted_at "
            + "from community.deleted_child_account where account_id = ?", SAME_ID))
            .containsEntry("guardian_id", GUARDIAN).containsEntry("deleted_at", "2026-11-16 10:00:00.123456");
        assertThatThrownBy(() -> callerService.checkActive(FavoriteActor.child(SAME_ID, GUARDIAN)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED));
        // 같은 ID의 회원 · 다른 자녀 계정은 막지 않는다
        assertThat(callerService.checkActive(FavoriteActor.member(SAME_ID))).isEqualTo(FavoriteActor.member(SAME_ID));
        assertThat(callerService.checkActive(FavoriteActor.child(SAME_ID + 1, GUARDIAN)).id()).isEqualTo(8L);
    }

    private List<Long> mine(FavoriteOwner owner) {
        return queryService.findMine(owner, null, null, null).items().stream()
            .map(FavoriteQueryService.FavoriteItem::productId).toList();
    }
}
