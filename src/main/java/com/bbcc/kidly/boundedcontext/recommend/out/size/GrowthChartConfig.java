package com.bbcc.kidly.boundedcontext.recommend.out.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.AgeGroup;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChartRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.WeightPercentiles;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * 체형표 CSV를 앱이 켜질 때 한 번 읽어 GrowthChart 빈으로 둔다. app은 GrowthChart만 주입받는다.
 */
@Configuration
public class GrowthChartConfig {

    private static final String CSV_PATH = "data/recommend/growth_chart.csv";
    private static final String COMMENT_PREFIX = "#";
    private static final String HEADER_PREFIX = "age_group";

    @Bean
    public GrowthChart growthChart() {
        List<GrowthChartRow> rows = new ArrayList<>();
        for (String line : readLines()) {
            if (isDataLine(line)) {
                rows.add(toRow(line));
            }
        }
        return GrowthChart.of(rows);
    }

    private static List<String> readLines() {
        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("체형표 CSV를 읽지 못했습니다: " + CSV_PATH, e);
        }
    }

    private static boolean isDataLine(String line) {
        return !line.isBlank() && !line.startsWith(COMMENT_PREFIX) && !line.startsWith(HEADER_PREFIX);
    }

    private static GrowthChartRow toRow(String line) {
        String[] columns = line.split(",");
        WeightPercentiles percentiles = WeightPercentiles.of(
                Double.parseDouble(columns[3]), Double.parseDouble(columns[4]));
        return GrowthChartRow.of(
                AgeGroup.valueOf(columns[0]), Gender.valueOf(columns[1]), Double.parseDouble(columns[2]), percentiles);
    }
}
