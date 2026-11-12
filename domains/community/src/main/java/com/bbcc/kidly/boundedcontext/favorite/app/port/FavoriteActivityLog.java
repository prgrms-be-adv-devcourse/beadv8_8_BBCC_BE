package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.util.List;

/**
 * 찜 쪽 활동 기록. 원래 변경과 같은 트랜잭션 안에서, 실제로 찜이 생기거나 지워졌을 때만 부른다(멱등 요청은 부르지 않는다).
 * 회원 ID는 찜 이벤트 본문에만 쓰인다(활동 행에는 남지 않는다). 구현은 out/activity(활동 컨텍스트의 들어오는 포트를 부른다).
 */
public interface FavoriteActivityLog {

    void favoriteAdded(Long favoriteId, Long memberId, Long productId);

    /**
     * 회원이 찜을 지웠다.
     */
    void favoriteRemoved(Long favoriteId, Long memberId, Long productId);

    /**
     * 회원 탈퇴로 지운 찜들(지운 행마다 하나, 한 문장으로 남긴다).
     */
    void favoritesRemovedByWithdrawal(Long memberId, List<RemovedFavorite> removed);

    record RemovedFavorite(Long favoriteId, Long productId) {
    }
}
