package com.bbcc.kidly.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Flyway 마이그레이션 파일 이름 규칙. 버전은 파일을 만든 시각(yyyyMMddHHmm, KST)이라 PR끼리 번호가 겹치지 않는다.
 * 콜백(beforeValidate 등)은 버전이 없는 SQL이라 따로 허용한다.
 */
class MigrationNamingTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d{12})__[a-z0-9_]+\\.sql$");
    private static final Pattern CALLBACK = Pattern.compile("^beforeValidate__[a-z0-9_]+\\.sql$");

    @Test
    @DisplayName("마이그레이션은 V{yyyyMMddHHmm}__설명.sql이고, 콜백만 예외다")
    void namesFollowTimestampRule() throws IOException {
        List<String> invalid = migrationFileNames().stream()
            .filter(name -> !VERSIONED.matcher(name).matches() && !CALLBACK.matcher(name).matches())
            .toList();

        assertThat(invalid).isEmpty();
    }

    @Test
    @DisplayName("같은 버전이 두 번 없다")
    void versionsAreUnique() throws IOException {
        List<String> versions = migrationFileNames().stream()
            .map(VERSIONED::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .toList();

        assertThat(versions).doesNotHaveDuplicates().isNotEmpty();
    }

    // 앱의 db/migration과 도메인 모듈 jar의 db/migration/{스키마}/를 모두 본다 (classpath*: 모든 jar, **: 하위 폴더)
    private static List<String> migrationFileNames() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/**/*.sql");
        return Arrays.stream(resources).map(Resource::getFilename).filter(Objects::nonNull).toList();
    }
}
