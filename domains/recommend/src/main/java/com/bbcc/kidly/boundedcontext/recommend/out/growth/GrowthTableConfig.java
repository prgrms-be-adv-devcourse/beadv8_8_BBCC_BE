package com.bbcc.kidly.boundedcontext.recommend.out.growth;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthRatioRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthRatioTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
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
 * 성장 기준표 CSV를 앱이 켜질 때 한 번 읽어 GrowthVelocityTable · GirthRatioTable 빈으로 둔다. app은 이 표들만 주입받는다.
 */
@Configuration
public class GrowthTableConfig {

    private static final String GROWTH_VELOCITY_PATH = "data/recommend/growth_velocity.csv";
    private static final String GROWTH_VELOCITY_SOURCE = "한국아동패널";
    private static final String GIRTH_RATIO_PATH = "data/recommend/girth_ratio.csv";
    private static final String COMMENT_PREFIX = "#";
    private static final String HEADER_PREFIX = "gender";

    @Bean
    public GrowthVelocityTable growthVelocityTable() {
        List<GrowthVelocityRow> rows = new ArrayList<>();
        for (String[] columns : readDataRows(GROWTH_VELOCITY_PATH)) {
            rows.add(new GrowthVelocityRow(Gender.valueOf(columns[0]), Integer.parseInt(columns[1]),
                    Double.parseDouble(columns[2]), Double.parseDouble(columns[3])));
        }
        return GrowthVelocityTable.of(rows, GROWTH_VELOCITY_SOURCE);
    }

    @Bean
    public GirthRatioTable girthRatioTable() {
        List<GirthRatioRow> rows = new ArrayList<>();
        for (String[] columns : readDataRows(GIRTH_RATIO_PATH)) {
            rows.add(new GirthRatioRow(Gender.valueOf(columns[0]), Integer.parseInt(columns[1]),
                    Double.parseDouble(columns[2]), Double.parseDouble(columns[3]), Double.parseDouble(columns[4])));
        }
        return GirthRatioTable.of(rows);
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
            throw new UncheckedIOException("성장 기준표 CSV를 읽지 못했습니다: " + path, e);
        }
    }

    private static boolean isDataLine(String line) {
        return !line.isBlank() && !line.startsWith(COMMENT_PREFIX) && !line.startsWith(HEADER_PREFIX);
    }
}
