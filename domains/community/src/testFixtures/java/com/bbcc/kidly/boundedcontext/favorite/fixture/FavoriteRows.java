package com.bbcc.kidly.boundedcontext.favorite.fixture;

import com.bbcc.kidly.boundedcontext.review.fixture.InsertRow;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 찜 컨텍스트 표(찜 · 찜 수 · 지운 자녀 계정)에 테스트 행을 넣는다. 찜 행은 소유자 칸(owner_type · owner_id · guardian_id)과
 * member_id(보호자 회원 ID)를 운영과 같은 규칙으로 채운다.
 */
public final class FavoriteRows {

    private final JdbcTemplate jdbc;

    public FavoriteRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 회원 본인 찜(소유자 ID = 보호자 ID = 회원 ID). 기본: 생성 · 수정 시각 now().
     */
    public FavoriteRow memberFavorite(long memberId, long productId) {
        return new FavoriteRow(jdbc, "MEMBER", memberId, memberId, productId);
    }

    /**
     * 자녀 계정 찜(소유자 ID = 자녀 계정 ID, member_id · guardian_id = 보호자 회원 ID).
     */
    public FavoriteRow childFavorite(long accountId, long guardianId, long productId) {
        return new FavoriteRow(jdbc, "CHILD", accountId, guardianId, productId);
    }

    /**
     * 찜 수 행을 그 값으로 만든다(이미 있으면 실패).
     */
    public void favoriteCount(long productId, long count) {
        jdbc.update("insert into community.favorite_stats (product_id, favorite_count, created_at, updated_at) "
            + "values (?, ?, now(), now())", productId, count);
    }

    /**
     * 찜 수를 1 올린다(행이 없으면 1로 만든다).
     */
    public void increaseFavoriteCount(long productId) {
        jdbc.update("insert into community.favorite_stats (product_id, favorite_count, created_at, updated_at) "
            + "values (?, 1, now(), now()) on conflict (product_id) do update "
            + "set favorite_count = community.favorite_stats.favorite_count + 1", productId);
    }

    /**
     * 지운 자녀 계정으로 남긴다(삭제 · 생성 시각 now()).
     */
    public void deletedChildAccount(long accountId, long guardianId) {
        jdbc.update("insert into community.deleted_child_account (account_id, guardian_id, deleted_at, created_at) "
            + "values (?, ?, now(), now())", accountId, guardianId);
    }

    public static final class FavoriteRow extends InsertRow<FavoriteRow> {

        private FavoriteRow(JdbcTemplate jdbc, String ownerType, long ownerId, long guardianId, long productId) {
            super(jdbc, "favorite");
            set("member_id", guardianId);
            set("owner_type", ownerType);
            set("owner_id", ownerId);
            set("guardian_id", guardianId);
            set("product_id", productId);
            now("created_at");
            now("updated_at");
        }

        /**
         * 찜한 시각(생성 · 수정 시각을 같은 값으로).
         */
        public FavoriteRow at(LocalDateTime at) {
            return set("created_at", at).set("updated_at", at);
        }

        @Override
        protected FavoriteRow self() {
            return this;
        }
    }
}
