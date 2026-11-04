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
 * kidly-app MigrationNamingTest와 같은 규칙이다(kidly-app에는 회원 · 인증 · 판매자 jar가 없다).
 */
class MigrationNamingTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d{12})__[a-z0-9_]+\\.sql$");

    @Test
    @DisplayName("마이그레이션은 V{yyyyMMddHHmm}__설명.sql이다")
    void namesFollowTimestampRule() throws IOException {
        List<String> invalid = migrationFileNames("migration").stream()
            .filter(name -> !VERSIONED.matcher(name).matches())
            .toList();

        assertThat(invalid).isEmpty();
    }

    @Test
    @DisplayName("같은 버전이 두 번 없다")
    void versionsAreUnique() throws IOException {
        List<String> versions = migrationFileNames("migration").stream()
            .map(VERSIONED::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .toList();

        assertThat(versions).doesNotHaveDuplicates().isNotEmpty();
    }

    @Test
    @DisplayName("자녀 마이그레이션 이름과 버전은 자녀 이력 안에서 유일하다")
    void childProfileVersionsAreUnique() throws IOException {
        var names = migrationFileNames("childprofile-migration");
        assertThat(names).isNotEmpty().allMatch(name -> VERSIONED.matcher(name).matches());
        assertThat(names.stream().map(VERSIONED::matcher).filter(Matcher::matches)
            .map(matcher -> matcher.group(1)).toList()).doesNotHaveDuplicates();
    }

    private static List<String> migrationFileNames(String location) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/" + location + "/**/*.sql");
        return Arrays.stream(resources).map(Resource::getFilename).filter(Objects::nonNull).toList();
    }
}
