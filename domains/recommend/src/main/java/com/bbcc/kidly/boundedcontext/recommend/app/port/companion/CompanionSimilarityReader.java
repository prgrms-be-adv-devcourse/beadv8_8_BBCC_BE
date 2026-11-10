package com.bbcc.kidly.boundedcontext.recommend.app.port.companion;

import java.util.Collection;
import java.util.Map;

/**
 * 보고 있는 상품과 각 후보 상품의 코사인 유사도(1 - 코사인 거리)를 읽는다.
 * 값은 -1 ~ 1이고 자르지 않는다(자르기는 domain이 한다).
 * 임베딩 벡터가 없는 후보는 결과에 담지 않고, 보고 있는 상품에 벡터가 없으면 빈 Map을 돌려준다.
 */
public interface CompanionSimilarityReader {

    Map<Long, Double> similarities(Long viewedProductId, Collection<Long> productIds);
}
