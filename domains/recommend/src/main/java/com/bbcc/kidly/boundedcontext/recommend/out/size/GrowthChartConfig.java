package com.bbcc.kidly.boundedcontext.recommend.out.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.BmiForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForLengthRow;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 체형 기준표 CSV 세 개를 앱이 켜질 때 한 번 읽어 GrowthChart 빈으로 둔다. app은 GrowthChart만 주입받는다.
 */
@Configuration
public class GrowthChartConfig {

    private static final String BMI_FOR_AGE_PATH = "data/recommend/bmi_for_age.csv";
    private static final String INFANT_WEIGHT_FOR_AGE_PATH = "data/recommend/infant_weight_for_age.csv";
    private static final String INFANT_WEIGHT_FOR_LENGTH_PATH = "data/recommend/infant_weight_for_length.csv";
    private static final String COMMENT_PREFIX = "#";
    private static final String HEADER_PREFIX = "gender";

    @Bean
    public GrowthChart growthChart() {
        List<BmiForAgeRow> bmiForAge = new ArrayList<>();
        for (String[] columns : readDataRows(BMI_FOR_AGE_PATH)) {
            bmiForAge.add(new BmiForAgeRow(Gender.valueOf(columns[0]), Integer.parseInt(columns[1]),
                    Double.parseDouble(columns[2]), Double.parseDouble(columns[3])));
        }
        List<InfantWeightForAgeRow> infantWeightForAge = new ArrayList<>();
        for (String[] columns : readDataRows(INFANT_WEIGHT_FOR_AGE_PATH)) {
            infantWeightForAge.add(new InfantWeightForAgeRow(Gender.valueOf(columns[0]),
                    Integer.parseInt(columns[1]), Double.parseDouble(columns[2])));
        }
        List<InfantWeightForLengthRow> infantWeightForLength = new ArrayList<>();
        for (String[] columns : readDataRows(INFANT_WEIGHT_FOR_LENGTH_PATH)) {
            infantWeightForLength.add(new InfantWeightForLengthRow(Gender.valueOf(columns[0]),
                    Double.parseDouble(columns[1]), Double.parseDouble(columns[2])));
        }
        return GrowthChart.of(bmiForAge, infantWeightForAge, infantWeightForLength);
    }

    private static List<String[]> readDataRows(String path) {
        List<String[]> rows = new ArrayList<>();
        for (String line : readLines(path)) {
            if (isDataLine(line)) {
                rows.add(line.split(","));
            }
        }
        return rows;
    }

    private static List<String> readLines(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("체형 기준표 CSV를 읽지 못했습니다: " + path, e);
        }
    }

    private static boolean isDataLine(String line) {
        return !line.isBlank() && !line.startsWith(COMMENT_PREFIX) && !line.startsWith(HEADER_PREFIX);
    }
}
