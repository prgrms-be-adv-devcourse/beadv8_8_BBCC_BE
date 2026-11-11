package com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
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
    // 무릎 · 복사뼈 언저리 2cm 안이면 그 관절에 닿는다고 본다
    private static final double JOINT_RANGE_CM = 2.0;

    private final BodyHeightTable table;

    public WearPreviewCalculator(BodyHeightTable table) {
        this.table = requireNonNull(table, "table은 필수입니다");
    }

    public Optional<WearPreview> preview(FitProduct product, ChildSizeInput child, LocalDate today) {
        long months = Period.between(child.birthDate(), today).toTotalMonths();
        BodyHeightRow ratios = table.ratios(child.gender(), months);
        GarmentPart garment = GarmentPart.ofCategoryPath(product.categoryPathNames());
        double hemBaseCm = hemBaseHeightCm(garment, child.heightCm(), ratios);
        List<WearPreview.SizeWear> sizes = new ArrayList<>();
        for (FitProduct.SizeMeasurements measurements : product.sizes()) {
            sizes.add(sizeWear(measurements, hemBaseCm, child.heightCm(), ratios));
        }
        return Optional.of(new WearPreview(garment, bodyOf(child.heightCm(), ratios), sizes));
    }

    /**
     * 밑단 높이를 잴 기준 높이다. 윗옷은 목뒤에서, 아래옷은 허리에서 내려온다. 반올림하기 전 값이다.
     */
    private static double hemBaseHeightCm(GarmentPart garment, double heightCm, BodyHeightRow ratios) {
        if (garment == GarmentPart.BOTTOM || garment == GarmentPart.SKIRT) {
            return heightCm * ratios.waist();
        }
        return heightCm * ratios.neck();
    }

    private static WearPreview.SizeWear sizeWear(FitProduct.SizeMeasurements measurements, double hemBaseCm,
                                                 double heightCm, BodyHeightRow ratios) {
        double totalLengthCm = measurements.measurements().get(TOTAL_LENGTH).doubleValue();
        double hemCm = hemBaseCm - totalLengthCm;
        return new WearPreview.SizeWear(measurements.size(), roundToTenth(hemCm),
                hemPositionOf(hemCm, heightCm, ratios), null, null);
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
