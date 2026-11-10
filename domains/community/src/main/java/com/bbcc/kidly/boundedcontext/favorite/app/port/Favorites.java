package com.bbcc.kidly.boundedcontext.favorite.app.port;

import com.bbcc.kidly.boundedcontext.favorite.domain.Favorite;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * 찜 저장소. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface Favorites {

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    /**
     * 바로 DB에 반영해 (회원, 상품) 유일 제약 위반을 이 호출에서 드러낸다.
     */
    Favorite saveAndFlush(Favorite favorite);

    /**
     * 그 회원의 그 상품 찜을 지운다(한 문장, delete … returning).
     *
     * @return 지운 찜 ID(없으면 빈 목록, 있으면 하나)
     */
    List<Long> deleteByMember(Long memberId, Long productId);

    /**
     * 내 찜 첫 쪽: 최신순(created_at desc, id desc).
     */
    List<FavoriteRow> findMine(Long memberId, Limit limit);

    /**
     * 내 찜 다음 쪽: (찜 시각, id)가 커서보다 뒤인 것.
     */
    List<FavoriteRow> findMineAfter(Long memberId, LocalDateTime createdAt, Long id, Limit limit);

    /**
     * 준 상품 중 내가 찜한 것(최신순).
     */
    List<FavoriteRow> findMineIn(Long memberId, Collection<Long> productIds);

    /**
     * 찜 한 줄(상품 정보는 따로 묶어 읽는다).
     */
    interface FavoriteRow {

        Long getId();

        Long getProductId();

        LocalDateTime getCreatedAt();
    }
}
