package com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import java.util.List;

/**
 * 자녀가 상품 사이즈를 입었을 때의 착용 미리보기다.
 *
 * @param garment 상품이 속한 옷 종류다
 * @param body 자녀의 몸 높이다
 * @param sizes 사이즈별 착용 결과다
 */
public record WearPreview(GarmentPart garment, Body body, List<SizeWear> sizes) {

    public WearPreview {
        sizes = List.copyOf(sizes);
    }

    /**
     * 자녀의 몸 높이(cm)다. 팔은 길이다. 바닥에서 잰 높이는 키 × 나이 · 성별 비율로 구한다.
     */
    public record Body(double neckCm, double shoulderCm, double waistCm, double hipCm, double crotchCm,
                       double kneeCm, double ankleCm, double armCm) {
    }

    /**
     * 사이즈 하나의 착용 결과다. 밑단 · 소매 높이(cm)와 그 위치다. 해당하는 실측이 없으면 null이다.
     */
    public record SizeWear(String size, Double hemCm, HemPosition hemAt, Double sleeveCm,
                           SleevePosition sleeveAt) {
    }
}
