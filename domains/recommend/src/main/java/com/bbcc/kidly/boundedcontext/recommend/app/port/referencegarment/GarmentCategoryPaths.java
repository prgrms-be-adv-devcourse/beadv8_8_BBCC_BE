package com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import java.util.List;

/**
 * 기준 옷의 세부 종류 카테고리를 상품 카테고리에서 찾는 나가는 연결의 요청서다.
 */
public interface GarmentCategoryPaths {

    /**
     * 경로에 그 카테고리가 들어 있는 상품 하나의 카테고리 경로 전체를 대분류부터 말단 순서로 돌려준다.
     * 그 카테고리의 상품이 추천에 하나도 없으면 빈 목록이다. 구분에 맞는 카테고리인지는 판단하지 않는다.
     */
    List<GarmentCategoryPathEntry> findPathOf(Long categoryId);
}
