package com.bbcc.kidly.boundedcontext.review.app.port.in;

import java.util.Collection;
import java.util.Map;

/**
 * 상품 복사본의 화면 정보를 묶어서 읽는다(한 쿼리). 다른 컨텍스트가 상품 이름 · 썸네일 · 표시 상태를 보여 줄 때 쓴다.
 */
public interface ProductCatalog {

    /**
     * @return 상품 ID별 정보. 복사본에 없는 상품은 빠진다
     */
    Map<Long, ProductInfo> find(Collection<Long> productIds);

    /**
     * @param thumbnailUrl 썸네일이 없으면 null
     * @param displayStatus 표시 상태 이름(ON_SALE · SOLD_OUT · SALE_ENDED)
     * @param saleEnded 표시 상태가 판매 종료인지
     */
    record ProductInfo(Long productId, String name, String thumbnailUrl, String displayStatus, boolean saleEnded) {
    }
}
