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
        return new GrowthSpeed(tableCmPerMonth, null, records.size(), recordMonths, 0.0, tableCmPerMonth,
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
