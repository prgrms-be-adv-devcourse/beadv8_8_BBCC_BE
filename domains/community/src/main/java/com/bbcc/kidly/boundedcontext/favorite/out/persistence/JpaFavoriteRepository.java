package com.bbcc.kidly.boundedcontext.favorite.out.persistence;

import com.bbcc.kidly.boundedcontext.favorite.app.port.Favorites;
import com.bbcc.kidly.boundedcontext.favorite.domain.Favorite;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 찜 저장소. 목록 커서 조건은 (찜 시각, id) 튜플 비교라 인덱스 (member_id, created_at desc, id desc)의 범위 조건으로 쓰인다.
 */
public interface JpaFavoriteRepository extends JpaRepository<Favorite, Long>, Favorites {

    String MINE = "select f.id as id, f.productId as productId, f.createdAt as createdAt "
        + "from CommunityFavorite f where f.memberId = :memberId";
    String NEWEST_FIRST = " order by f.createdAt desc, f.id desc";

    @Override
    @Modifying
    @Query("delete from CommunityFavorite f where f.memberId = :memberId and f.productId = :productId")
    int deleteByMember(@Param("memberId") Long memberId, @Param("productId") Long productId);

    @Override
    @Query(MINE + NEWEST_FIRST)
    List<FavoriteRow> findMine(@Param("memberId") Long memberId, Limit limit);

    @Override
    @Query(MINE + " and (f.createdAt, f.id) < (:createdAt, :id)" + NEWEST_FIRST)
    List<FavoriteRow> findMineAfter(@Param("memberId") Long memberId, @Param("createdAt") LocalDateTime createdAt,
        @Param("id") Long id, Limit limit);

    @Override
    @Query(MINE + " and f.productId in :productIds" + NEWEST_FIRST)
    List<FavoriteRow> findMineIn(@Param("memberId") Long memberId,
        @Param("productIds") Collection<Long> productIds);
}
