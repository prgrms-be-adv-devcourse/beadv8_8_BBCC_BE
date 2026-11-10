package com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import java.util.List;

public interface GarmentCategoryPaths {

    /**
     * 상품의 말단 카테고리가 그 카테고리인 상품 중 가장 늦게 반영된 상품의 카테고리 경로 전체를
     * 대분류부터 말단 순서로 돌려준다. 말단이 아닌 카테고리이거나 그 카테고리의 상품이 추천에 하나도 없으면
     * 빈 목록이다. 구분에 맞는 카테고리인지는 판단하지 않는다.
     */
    List<GarmentCategoryPathEntry> findPathOf(Long categoryId);
}
