package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * 아이의 성장 기록과 나이 · 성별 표 값으로 키 성장 속도를 계산한다.
 */
public class GrowthVelocityCalculator {

    private static final int MONTHS_PER_YEAR = 12;
    // 한 해 365.25일을 12로 나눈 한 달의 평균 일수다
    private static final double DAYS_PER_MONTH = 30.4375;
    // 집에서 잰 키의 오차를 0.5 ~ 1cm로 보고 그 가운데 값을 쓴다
    private static final double MEASUREMENT_ERROR_CM = 0.75;
    // 표 값의 2배는 사춘기 후반에 너무 낮아 늦게 크는 아이의 실제 속도를 자른다. 중앙값에서 표준편차 2배까지는 정상 범위로 본다
    private static final double MAX_SD_MULTIPLE = 2.0;
    public static final int RECENT_MONTHS = 3;
    // 사춘기 앞뒤로 성장 속도가 해마다 크게 바뀌어 1년보다 오래된 기록은 지금 속도를 잘못 알려 준다
    public static final int WINDOW_MONTHS = 12;

    private final GrowthVelocityTable table;

    public GrowthVelocityCalculator(GrowthVelocityTable table) {
        this.table = requireNonNull(table, "table은 필수입니다");
    }

    public GrowthVelocity calculate(Gender gender, LocalDate birthDate, List<RecommendGrowthRecord> allRecords,
            LocalDate today) {
        requireNonNull(gender, "gender는 필수입니다");
        requireNonNull(birthDate, "birthDate는 필수입니다");
        requireNonNull(allRecords, "records는 필수입니다");
        requireNonNull(today, "today는 필수입니다");
        List<RecommendGrowthRecord> records = recordsWithin(allRecords, WINDOW_MONTHS, today);
        long ageMonths = Period.between(birthDate, today).toTotalMonths();
        double tableCmPerMonth = table.yearlyCm(gender, ageMonths) / MONTHS_PER_YEAR;
        double tableError = table.yearlySdCm(gender, ageMonths) / MONTHS_PER_YEAR;
        double maxCmPerMonth = tableCmPerMonth + MAX_SD_MULTIPLE * tableError;
        Slope slope = leastSquaresSlope(records);
        Double recentCmPerMonth = recentCmPerMonth(records, today, maxCmPerMonth);
        if (!slope.coversMonth()) {
            return new GrowthVelocity(tableCmPerMonth, recentCmPerMonth, null, records.size(), slope.months(), 0.0,
                    tableCmPerMonth, table.source());
        }
        return blend(records.size(), slope, recentCmPerMonth, new TableValue(tableCmPerMonth, tableError,
                maxCmPerMonth));
    }

    /**
     * 표에서 읽은 값이다. 상한은 중앙값에서 표준편차 2배까지 더한 속도다.
     */
    private record TableValue(double cmPerMonth, double error, double maxCmPerMonth) {
    }

    /**
     * 아이 기록의 최소제곱 기울기와 표 값을, 오차가 작은 쪽에 비중을 두어 섞는다.
     */
    private GrowthVelocity blend(int recordCount, Slope slope, Double recentCmPerMonth, TableValue tableValue) {
        double tableError = tableValue.error();
        double childErrorSquare = MEASUREMENT_ERROR_CM * MEASUREMENT_ERROR_CM / slope.timeSpread();
        double childShare = tableError * tableError / (tableError * tableError + childErrorSquare);
        double blended = childShare * slope.cmPerMonth() + (1 - childShare) * tableValue.cmPerMonth();
        double cmPerMonth = limit(blended, tableValue.maxCmPerMonth());
        return new GrowthVelocity(cmPerMonth, recentCmPerMonth, slope.cmPerMonth(), recordCount, slope.months(),
                childShare, tableValue.cmPerMonth(), table.source());
    }

    /**
     * 오늘부터 3개월 안 기록만으로 구한 속도다. 표와 섞지 않으며, 기록이 부족하면 null이다.
     */
    private Double recentCmPerMonth(List<RecommendGrowthRecord> records, LocalDate today, double maxCmPerMonth) {
        List<RecommendGrowthRecord> recentRecords = recordsWithin(records, RECENT_MONTHS, today);
        Slope recentSlope = leastSquaresSlope(recentRecords);
        if (!recentSlope.coversMonth()) {
            return null;
        }
        return limit(recentSlope.cmPerMonth(), maxCmPerMonth);
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

    // 집에서 잰 키의 오차로 기울기가 크게 흔들릴 수 있어 0 ~ (표 중앙값 + 표준편차 2배) 안으로 맞춘다
    private double limit(double cmPerMonth, double maxCmPerMonth) {
        return Math.clamp(cmPerMonth, 0, maxCmPerMonth);
    }

    /**
     * 기록이 1개 이하이거나 기록 날짜가 모두 같으면 기울기를 계산하지 않고 기간 0 · 한 달 미만으로 돌려준다.
     */
    private Slope leastSquaresSlope(List<RecommendGrowthRecord> records) {
        int count = records.size();
        if (count <= 1) {
            return new Slope(0, 0, 0, false);
        }
        double[] times = new double[count];
        double[] heights = new double[count];
        double timeSum = 0;
        double heightSum = 0;
        long firstDay = Long.MAX_VALUE;
        long lastDay = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            RecommendGrowthRecord record = records.get(i);
            long epochDay = record.getRecordedAt().toLocalDate().toEpochDay();
            firstDay = Math.min(firstDay, epochDay);
            lastDay = Math.max(lastDay, epochDay);
            times[i] = epochDay / DAYS_PER_MONTH;
            heights[i] = record.getHeightCm().doubleValue();
            timeSum += times[i];
            heightSum += heights[i];
        }
        if (firstDay == lastDay) {
            return new Slope(0, 0, 0, false);
        }
        double timeMean = timeSum / count;
        double heightMean = heightSum / count;
        double timeSpread = 0;
        double covariance = 0;
        for (int i = 0; i < count; i++) {
            timeSpread += (times[i] - timeMean) * (times[i] - timeMean);
            covariance += (times[i] - timeMean) * (heights[i] - heightMean);
        }
        // 한 달 이상인지는 달력으로 본다. 30.4375는 기울기의 시간 단위로만 쓴다
        boolean coversMonth = !LocalDate.ofEpochDay(firstDay).plusMonths(1).isAfter(LocalDate.ofEpochDay(lastDay));
        return new Slope(covariance / timeSpread, timeSpread, (lastDay - firstDay) / DAYS_PER_MONTH, coversMonth);
    }

    /**
     * 최소제곱 기울기(cm/월), 기록 시점(개월)의 평균에서 벌어진 정도의 제곱합, 첫 기록과 마지막 기록 사이 개월 수,
     * 그 기간이 달력으로 한 달 이상인지다.
     */
    private record Slope(double cmPerMonth, double timeSpread, double months, boolean coversMonth) {
    }
}
