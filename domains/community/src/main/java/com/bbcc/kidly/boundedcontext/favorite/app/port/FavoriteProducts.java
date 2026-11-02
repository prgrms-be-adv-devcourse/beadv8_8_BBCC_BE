package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.util.Collection;
import java.util.Map;

/**
 * 찜 화면에 보여 줄 상품 정보(이름 · 썸네일 · 표시 상태)와 찜할 수 있는지. 구현은 out 어댑터가 상품 정보를 가진 쪽에서 묶어 읽는다.
 */
public interface FavoriteProducts {

    /**
     * @return 상품 ID별 정보. 정보가 없는 상품(아직 받지 못했거나 판매 시작 전)은 빠진다
     */
    Map<Long, ProductView> find(Collection<Long> productIds);

    /**
     * @param displayStatus 표시 상태 이름(ON_SALE · SOLD_OUT · SALE_ENDED)
     * @param saleEnded 판매 종료인지(새로 찜할 수 없다)
     */
    record ProductView(Long productId, String name, String thumbnailUrl, String displayStatus, boolean saleEnded) {
    }
}
