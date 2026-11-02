package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.time.LocalDateTime;

/**
 * 상품별 찜 수 증감. 둘 다 한 문장이라 동시에 증감해도 빠지지 않는다.
 */
public interface FavoriteStatsCounter {

    /**
     * 찜 +1. 첫 찜이면 행을 만든다(INSERT … ON CONFLICT DO UPDATE).
     */
    void increase(Long productId, LocalDateTime now);

    /**
     * 찜 −count(있는 행 UPDATE).
     *
     * @return 바뀐 행 수. 0이면 찜 수 행이 없다(찜이 있는데 찜 수가 없는 어긋난 상태)
     */
    int decreaseBy(Long productId, int count, LocalDateTime now);
}
