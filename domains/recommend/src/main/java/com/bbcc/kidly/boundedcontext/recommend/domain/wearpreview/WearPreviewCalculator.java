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
            sizes.add(sizeWear(measurements, hemBaseCm));
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

    private static WearPreview.SizeWear sizeWear(FitProduct.SizeMeasurements measurements, double hemBaseCm) {
        double totalLengthCm = measurements.measurements().get(TOTAL_LENGTH).doubleValue();
        return new WearPreview.SizeWear(measurements.size(), roundToTenth(hemBaseCm - totalLengthCm),
                null, null, null);
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
