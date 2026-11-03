package com.bbcc.kidly.boundedcontext.favorite.out.persistence;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsReader;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteStats;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상품별 찜 수. 증가는 행이 없으면 넣고 있으면 더하는 upsert, 감소는 있는 행을 그 자리에서 바꾸는 UPDATE(감소를 upsert로 하면
 * 넣으려던 음수 행이 CHECK(0 이상)에 먼저 걸린다).
 */
public interface JpaFavoriteStatsRepository
    extends JpaRepository<FavoriteStats, Long>, FavoriteStatsCounter, FavoriteStatsReader {

    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into community.favorite_stats (product_id, favorite_count, created_at, updated_at)
        values (:productId, 1, :now, :now)
        on conflict (product_id) do update
        set favorite_count = community.favorite_stats.favorite_count + 1,
            updated_at = excluded.updated_at
        """)
    void increase(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
        update community.favorite_stats
        set favorite_count = favorite_count - 1,
            updated_at = :now
        where product_id = :productId
        """)
    int decrease(@Param("productId") Long productId, @Param("now") LocalDateTime now);
}
