package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private static final double MAX_TABLE_MULTIPLE = 2.0;
    private static final int RECENT_MONTHS = 3;
    // 사춘기 앞뒤로 성장 속도가 해마다 크게 바뀌어 1년보다 오래된 기록은 지금 속도를 잘못 알려 준다
    private static final int WINDOW_MONTHS = 12;

    private final GrowthVelocityTable table;

    public GrowthSpeedCalculator(GrowthVelocityTable table) {
        this.table = requireNonNull(table, "table은 필수입니다");
    }

    public GrowthSpeed calculate(Gender gender, LocalDate birthDate, List<RecommendGrowthRecord> allRecords,
            LocalDate today) {
        List<RecommendGrowthRecord> records = recordsWithin(allRecords, WINDOW_MONTHS, today);
        long ageMonths = Period.between(birthDate, today).toTotalMonths();
        double tableCmPerMonth = table.yearlyCm(gender, ageMonths) / MONTHS_PER_YEAR;
        double recordMonths = recordMonths(records);
        Double recentCmPerMonth = recentCmPerMonth(records, today, tableCmPerMonth);
        if (recordMonths < MIN_RECORD_MONTHS) {
            return new GrowthSpeed(tableCmPerMonth, recentCmPerMonth, null, records.size(), recordMonths, 0.0,
                    tableCmPerMonth, TABLE_SOURCE);
        }
        double tableError = table.yearlySdCm(gender, ageMonths) / MONTHS_PER_YEAR;
        return blend(records, recordMonths, recentCmPerMonth, tableCmPerMonth, tableError);
    }

    /**
     * 아이 기록의 최소제곱 기울기와 표 값을, 오차가 작은 쪽에 비중을 두어 섞는다.
     */
    private GrowthSpeed blend(List<RecommendGrowthRecord> records, double recordMonths, Double recentCmPerMonth,
            double tableCmPerMonth, double tableError) {
        Slope slope = leastSquaresSlope(records);
        double childErrorSquare = MEASUREMENT_ERROR_CM * MEASUREMENT_ERROR_CM / slope.timeSpread();
        double childShare = tableError * tableError / (tableError * tableError + childErrorSquare);
        double blended = childShare * slope.cmPerMonth() + (1 - childShare) * tableCmPerMonth;
        double cmPerMonth = limitByTable(blended, tableCmPerMonth);
        return new GrowthSpeed(cmPerMonth, recentCmPerMonth, slope.cmPerMonth(), records.size(), recordMonths,
                childShare, tableCmPerMonth, TABLE_SOURCE);
    }

    /**
     * 오늘부터 3개월 안 기록만으로 구한 속도다. 표와 섞지 않으며, 기록이 부족하면 null이다.
     */
    private Double recentCmPerMonth(List<RecommendGrowthRecord> records, LocalDate today, double tableCmPerMonth) {
        List<RecommendGrowthRecord> recentRecords = recordsWithin(records, RECENT_MONTHS, today);
        if (recordMonths(recentRecords) < MIN_RECORD_MONTHS) {
            return null;
        }
        return limitByTable(leastSquaresSlope(recentRecords).cmPerMonth(), tableCmPerMonth);
    }

    /**
     * 기록 날짜가 오늘부터 months개월 전 날짜 이상, 오늘 이하인 기록만 고른다.
     */
    private List<RecommendGrowthRecord> recordsWithin(List<RecommendGrowthRecord> records, int months,
            LocalDate today) {
        LocalDate from = today.minusMonths(months);
        List<RecommendGrowthRecord> selected = new ArrayList<>();
        for (RecommendGrowthRecord record : records) {
            LocalDate recordedOn = record.getRecordedAt().toLocalDate();
            if (!recordedOn.isBefore(from) && !recordedOn.isAfter(today)) {
                selected.add(record);
            }
        }
        return selected;
    }

    // 집에서 잰 키의 오차로 기울기가 크게 흔들릴 수 있어 0 ~ 표 값의 2배 안으로 맞춘다
    private double limitByTable(double cmPerMonth, double tableCmPerMonth) {
        return Math.clamp(cmPerMonth, 0, MAX_TABLE_MULTIPLE * tableCmPerMonth);
    }

    private Slope leastSquaresSlope(List<RecommendGrowthRecord> records) {
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
        return new Slope(covariance / timeSpread, timeSpread);
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

    /**
     * 최소제곱 기울기(cm/월)와 기록 시점(개월)의 평균에서 벌어진 정도의 제곱합이다.
     */
    private record Slope(double cmPerMonth, double timeSpread) {
    }
}
