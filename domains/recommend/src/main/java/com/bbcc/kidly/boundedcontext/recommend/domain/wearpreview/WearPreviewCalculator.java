package com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 자녀의 몸 높이에 상품 사이즈를 대어 보고 착용 미리보기를 만든다.
 */
public class WearPreviewCalculator {

    private static final String TOTAL_LENGTH = "TOTAL_LENGTH";
    private static final String SLEEVE_LENGTH = "SLEEVE_LENGTH";
    // 무릎 · 복사뼈 · 손목 언저리 2cm 안이면 그 관절에 닿는다고 본다
    private static final double JOINT_RANGE_CM = 2.0;

    private final BodyHeightTable table;

    public WearPreviewCalculator(BodyHeightTable table) {
        this.table = requireNonNull(table, "table은 필수입니다");
    }

    public Optional<WearPreview> preview(FitProduct product, ChildSizeInput child, LocalDate today) {
        requireNonNull(product, "product는 필수입니다");
        requireNonNull(child, "child는 필수입니다");
        requireNonNull(today, "today는 필수입니다");
        requireNonNull(child.gender(), "gender는 필수입니다");
        requireNonNull(child.birthDate(), "birthDate는 필수입니다");
        // 기준 옷 비교와 같은 기준으로 고른다. 사이즈 무관 상품과 맞는 칸이 없는 상품(모자 · 양말)은 만들지 않는다
        GarmentPart garment = GarmentPart.ofCategoryPath(product.categoryPathNames());
        if (product.sizeFree() || garment == null) {
            return Optional.empty();
        }
        long months = Period.between(child.birthDate(), today).toTotalMonths();
        BodyHeightRow ratios = table.ratios(child.gender(), months);
        List<WearPreview.SizeWear> sizes = new ArrayList<>();
        for (FitProduct.SizeMeasurements measurements : product.sizes()) {
            sizes.add(sizeWear(measurements, garment, child.heightCm(), ratios));
        }
        return Optional.of(new WearPreview(garment, bodyOf(child.heightCm(), ratios), sizes));
    }

    private static boolean isBottomWear(GarmentPart garment) {
        return garment == GarmentPart.BOTTOM || garment == GarmentPart.SKIRT;
    }

    /**
     * 총장이 없는 사이즈는 밑단 칸이, 소매길이가 없는 사이즈와 소매가 없는 바지 · 치마는 소매 칸이 null이다.
     */
    private static WearPreview.SizeWear sizeWear(FitProduct.SizeMeasurements measurements, GarmentPart garment,
                                                 double heightCm, BodyHeightRow ratios) {
        boolean bottomWear = isBottomWear(garment);
        Double hemCm = null;
        HemPosition hemAt = null;
        BigDecimal totalLength = measurements.measurements().get(TOTAL_LENGTH);
        if (totalLength != null) {
            // 밑단을 잴 기준 높이다. 윗옷은 목뒤에서, 아래옷은 허리에서 내려온다. 반올림하기 전 값이다
            double baseCm = heightCm * (bottomWear ? ratios.waist() : ratios.neck());
            double rawHemCm = baseCm - totalLength.doubleValue();
            hemCm = roundToTenth(rawHemCm);
            hemAt = hemPositionOf(rawHemCm, heightCm, ratios);
        }
        Double sleeveCm = null;
        SleevePosition sleeveAt = null;
        BigDecimal sleeve = measurements.measurements().get(SLEEVE_LENGTH);
        if (sleeve != null && !bottomWear) {
            double rawSleeveCm = sleeve.doubleValue();
            sleeveCm = roundToTenth(rawSleeveCm);
            sleeveAt = sleevePositionOf(rawSleeveCm, heightCm * ratios.arm());
        }
        return new WearPreview.SizeWear(measurements.size(), hemCm, hemAt, sleeveCm, sleeveAt);
    }

    /**
     * 소매길이가 팔길이의 절반 이하면 팔꿈치, 팔길이 언저리면 손목, 그보다 길면 손에 닿는다고 본다.
     */
    private static SleevePosition sleevePositionOf(double sleeveCm, double armCm) {
        if (sleeveCm <= armCm / 2) {
            return SleevePosition.ELBOW;
        }
        if (sleeveCm < armCm - JOINT_RANGE_CM) {
            return SleevePosition.ABOVE_WRIST;
        }
        if (sleeveCm <= armCm + JOINT_RANGE_CM) {
            return SleevePosition.WRIST;
        }
        return SleevePosition.HAND;
    }

    /**
     * 밑단 높이가 몸의 어느 높이 사이에 있는지 위에서부터 차례로 본다. 반올림하기 전 값으로 판정한다.
     */
    private static HemPosition hemPositionOf(double hemCm, double heightCm, BodyHeightRow ratios) {
        double waistCm = heightCm * ratios.waist();
        double hipCm = heightCm * ratios.hip();
        double crotchCm = heightCm * ratios.crotch();
        double kneeCm = heightCm * ratios.knee();
        double ankleCm = heightCm * ratios.ankle();
        if (hemCm > waistCm) {
            return HemPosition.ABOVE_WAIST;
        }
        if (hemCm > hipCm) {
            return HemPosition.WAIST_HIP;
        }
        if (hemCm > crotchCm) {
            return HemPosition.HIP;
        }
        if (hemCm > (crotchCm + kneeCm) / 2) {
            return HemPosition.THIGH;
        }
        if (hemCm > kneeCm + JOINT_RANGE_CM) {
            return HemPosition.ABOVE_KNEE;
        }
        if (hemCm >= kneeCm - JOINT_RANGE_CM) {
            return HemPosition.KNEE;
        }
        if (hemCm > ankleCm + JOINT_RANGE_CM) {
            return HemPosition.BELOW_KNEE;
        }
        if (hemCm >= ankleCm - JOINT_RANGE_CM) {
            return HemPosition.ANKLE;
        }
        return HemPosition.FLOOR;
    }

    private static WearPreview.Body bodyOf(double heightCm, BodyHeightRow ratios) {
        return new WearPreview.Body(
                roundToTenth(heightCm * ratios.neck()),
                roundToTenth(heightCm * ratios.shoulder()),
                roundToTenth(heightCm * ratios.waist()),
                roundToTenth(heightCm * ratios.hip()),
                roundToTenth(heightCm * ratios.crotch()),
                roundToTenth(heightCm * ratios.knee()),
                roundToTenth(heightCm * ratios.ankle()),
                roundToTenth(heightCm * ratios.arm()));
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
