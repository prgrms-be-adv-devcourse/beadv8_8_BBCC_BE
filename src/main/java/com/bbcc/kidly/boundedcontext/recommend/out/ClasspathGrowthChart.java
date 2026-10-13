package com.bbcc.kidly.boundedcontext.recommend.out;

import com.bbcc.kidly.boundedcontext.recommend.domain.AgeGroup;
import com.bbcc.kidly.boundedcontext.recommend.domain.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.WeightPercentiles;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ClasspathGrowthChart implements GrowthChart {

    private static final String CSV_PATH = "data/recommend/growth_chart.csv";
    private static final String COMMENT_PREFIX = "#";
    private static final String HEADER_PREFIX = "age_group";

    private final Map<ChartKey, WeightPercentiles> rows;

    public ClasspathGrowthChart() {
        this.rows = load();
    }

    @Override
    public Optional<WeightPercentiles> find(AgeGroup ageGroup, Gender gender, double heightCm) {
        return Optional.ofNullable(rows.get(ChartKey.of(ageGroup, gender, heightCm)));
    }

    private static Map<ChartKey, WeightPercentiles> load() {
        Map<ChartKey, WeightPercentiles> rows = new HashMap<>();
        for (String line : readLines()) {
            if (isDataLine(line)) {
                putRow(rows, line);
            }
        }
        return Map.copyOf(rows);
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

    private static void putRow(Map<ChartKey, WeightPercentiles> rows, String line) {
        String[] columns = line.split(",");
        ChartKey key = ChartKey.of(
                AgeGroup.valueOf(columns[0]), Gender.valueOf(columns[1]), Double.parseDouble(columns[2]));
        WeightPercentiles percentiles = WeightPercentiles.of(
                Double.parseDouble(columns[3]), Double.parseDouble(columns[4]));
        rows.put(key, percentiles);
    }

    private record ChartKey(AgeGroup ageGroup, Gender gender, long heightMm) {

        private static ChartKey of(AgeGroup ageGroup, Gender gender, double heightCm) {
            return new ChartKey(ageGroup, gender, Math.round(heightCm * 10));
        }
    }
}
