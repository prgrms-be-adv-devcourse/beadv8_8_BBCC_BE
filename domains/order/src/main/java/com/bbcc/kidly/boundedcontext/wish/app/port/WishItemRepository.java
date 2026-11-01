package com.bbcc.kidly.boundedcontext.wish.app.port;

import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import java.util.List;
import java.util.Optional;

/**
 * 조르기 줄 저장소(#640). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface WishItemRepository {

    WishItem save(WishItem wishItem);

    Optional<WishItem> findByIdAndStatus(Long id, WishItem.Status status);

    /**
     * 같은 자녀 · 같은 옵션의 줄. 합치기 정책일 때만 찾으므로 한 줄 이하다.
     */
    Optional<WishItem> findFirstByChildIdAndOptionIdAndStatusOrderByIdAsc(
        Long childId, Long optionId, WishItem.Status status);

    /**
     * 최근에 담은 줄부터 돌려준다.
     */
    List<WishItem> findAllByChildIdAndStatusOrderByIdDesc(Long childId, WishItem.Status status);

    long countByChildIdAndStatus(Long childId, WishItem.Status status);

    void delete(WishItem wishItem);
}
