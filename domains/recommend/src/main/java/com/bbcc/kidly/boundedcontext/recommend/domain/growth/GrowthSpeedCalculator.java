package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 아이의 성장 기록과 나이 · 성별 표 값으로 키 성장 속도를 계산한다.
 */
public class GrowthSpeedCalculator {

    private static final int MONTHS_PER_YEAR = 12;
    // 한 해 365.25일을 12로 나눈 한 달의 평균 일수다
    private static final double DAYS_PER_MONTH = 30.4375;
    private static final double MIN_RECORD_MONTHS = 1.0;
    // 집에서 잰 키의 오차를 0.5 ~ 1cm로 보고 그 가운데 값을 쓴다
    private static final double MEASUREMENT_ERROR_CM = 0.75;
    private static final String TABLE_SOURCE = "한국아동패널";

    private final GrowthVelocityTable table;

    public GrowthSpeedCalculator(GrowthVelocityTable table) {
        this.table = requireNonNull(table, "table은 필수입니다");
    }

    public GrowthSpeed calculate(Gender gender, LocalDate birthDate, List<RecommendGrowthRecord> records,
            LocalDate today) {
        long ageMonths = Period.between(birthDate, today).toTotalMonths();
        double tableCmPerMonth = table.yearlyCm(gender, ageMonths) / MONTHS_PER_YEAR;
        double recordMonths = recordMonths(records);
        if (recordMonths < MIN_RECORD_MONTHS) {
            return new GrowthSpeed(tableCmPerMonth, null, null, records.size(), recordMonths, 0.0,
                    tableCmPerMonth, TABLE_SOURCE);
        }
        double tableError = table.yearlySdCm(gender, ageMonths) / MONTHS_PER_YEAR;
        return blend(records, recordMonths, tableCmPerMonth, tableError);
    }

    /**
     * 아이 기록의 최소제곱 기울기와 표 값을, 오차가 작은 쪽에 비중을 두어 섞는다.
     */
    private GrowthSpeed blend(List<RecommendGrowthRecord> records, double recordMonths, double tableCmPerMonth,
            double tableError) {
        int count = records.size();
        double[] times = new double[count];
        double[] heights = new double[count];
        double timeSum = 0;
        double heightSum = 0;
        for (int i = 0; i < count; i++) {
            RecommendGrowthRecord record = records.get(i);
            times[i] = record.getRecordedAt().toLocalDate().toEpochDay() / DAYS_PER_MONTH;
            heights[i] = record.getHeightCm().doubleValue();
            timeSum += times[i];
            heightSum += heights[i];
        }
        double timeMean = timeSum / count;
        double heightMean = heightSum / count;
        double timeSpread = 0;
        double covariance = 0;
        for (int i = 0; i < count; i++) {
            timeSpread += (times[i] - timeMean) * (times[i] - timeMean);
            covariance += (times[i] - timeMean) * (heights[i] - heightMean);
        }
        double childCmPerMonth = covariance / timeSpread;
        double childErrorSquare = MEASUREMENT_ERROR_CM * MEASUREMENT_ERROR_CM / timeSpread;
        double childShare = tableError * tableError / (tableError * tableError + childErrorSquare);
        double cmPerMonth = childShare * childCmPerMonth + (1 - childShare) * tableCmPerMonth;
        return new GrowthSpeed(cmPerMonth, null, childCmPerMonth, count, recordMonths, childShare, tableCmPerMonth,
                TABLE_SOURCE);
    }

    private double recordMonths(List<RecommendGrowthRecord> records) {
        if (records.size() <= 1) {
            return 0;
        }
        LocalDate first = records.get(0).getRecordedAt().toLocalDate();
        LocalDate last = first;
        for (RecommendGrowthRecord record : records) {
            LocalDate recordedOn = record.getRecordedAt().toLocalDate();
            if (recordedOn.isBefore(first)) {
                first = recordedOn;
            }
            if (recordedOn.isAfter(last)) {
                last = recordedOn;
            }
        }
        return ChronoUnit.DAYS.between(first, last) / DAYS_PER_MONTH;
    }
}
