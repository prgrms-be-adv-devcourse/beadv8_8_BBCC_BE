package com.bbcc.kidly.boundedcontext.favorite.app.port;

import com.bbcc.kidly.boundedcontext.favorite.domain.Favorite;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwnerType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * 찜 저장소. 구현은 out/persistence(Spring Data가 만든다). 찜은 소유자(종류, ID)마다 따로 다룬다(회원 7과 자녀 계정 7은 다른 소유자).
 */
public interface Favorites {

    boolean existsByOwnerTypeAndOwnerIdAndProductId(FavoriteOwnerType ownerType, Long ownerId, Long productId);

    /**
     * 바로 DB에 반영해 (소유자, 상품) 유일 제약 위반을 이 호출에서 드러낸다.
     */
    Favorite saveAndFlush(Favorite favorite);

    /**
     * 그 소유자의 그 상품 찜을 지운다(한 문장, delete … returning).
     *
     * @param ownerType 소유자 종류 이름(FavoriteOwnerType)
     * @return 지운 찜 ID(없으면 빈 목록, 있으면 하나)
     */
    List<Long> deleteByOwner(String ownerType, Long ownerId, Long productId);

    /**
     * 내 찜 첫 쪽: 최신순(created_at desc, id desc).
     */
    List<FavoriteRow> findMine(FavoriteOwnerType ownerType, Long ownerId, Limit limit);

    /**
     * 내 찜 다음 쪽: (찜 시각, id)가 커서보다 뒤인 것.
     */
    List<FavoriteRow> findMineAfter(FavoriteOwnerType ownerType, Long ownerId, LocalDateTime createdAt, Long id,
        Limit limit);

    /**
     * 준 상품 중 내가 찜한 것(최신순).
     */
    List<FavoriteRow> findMineIn(FavoriteOwnerType ownerType, Long ownerId, Collection<Long> productIds);

    /**
     * 찜 한 줄(상품 정보는 따로 묶어 읽는다).
     */
    interface FavoriteRow {

        Long getId();

        Long getProductId();

        LocalDateTime getCreatedAt();
    }
}
