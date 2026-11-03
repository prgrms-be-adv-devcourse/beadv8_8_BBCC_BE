package com.bbcc.kidly.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이 서비스의 이력 테이블이 아직 없으면, kidly-app이 이미 적용한 이 서비스의 마이그레이션으로 baseline을 잡는다.
 * 고정된 baseline-version으로는 두 DB를 함께 맞출 수 없다. dev · prod는 kidly-app이 도메인 마이그레이션까지 적용해 마지막 버전에서 잡아야 하고,
 * 회원 도메인이 빠진 kidly-app이 먼저 뜬 빈 DB는 kidly-app 파일(init 등)만 적용돼 그 뒤 도메인 마이그레이션을 이 서비스가 적용해야 한다.
 * 같은 버전이면 같은 변경을 적용한 것으로 본다. 버전 순으로 처음 빠진 버전 바로 앞에서 잡아, 빠진 버전을 건너뛰지 않는다.
 * baseline을 잡을 때는 스키마를 만들지 않는다. 만들면 그 기록으로 이력 테이블이 먼저 생겨 baseline 없이 init부터 돈다
 * (kidly-app은 auth 스키마를 만들지 않는다. auth는 도메인 마이그레이션이 만든다).
 * kidly-app 이력이 없거나 겹치는 버전이 없으면 설정 그대로 둔다. 모든 환경에 이 서비스의 이력 테이블이 생기면 지운다.
 */
@Component
class MemberFlywayMigrationStrategy implements FlywayMigrationStrategy {

    private static final String KIDLY_APP_HISTORY = "public.flyway_schema_history";

    @Override
    public void migrate(Flyway flyway) {
        kidlyAppAppliedVersion(flyway)
            .map(version -> Flyway.configure().configuration(flyway.getConfiguration())
                .baselineVersion(version)
                .createSchemas(false)
                .load())
            .orElse(flyway)
            .migrate();
    }

    private static Optional<MigrationVersion> kidlyAppAppliedVersion(Flyway flyway) {
        var info = flyway.info();
        if (info.applied().length > 0) {
            return Optional.empty();
        }
        var jdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());
        var hasKidlyAppHistory = jdbc.queryForObject(
            "SELECT to_regclass(?) IS NOT NULL", Boolean.class, KIDLY_APP_HISTORY);
        if (!Boolean.TRUE.equals(hasKidlyAppHistory)) {
            return Optional.empty();
        }
        var applied = new HashSet<>(jdbc.queryForList(
            "SELECT version FROM " + KIDLY_APP_HISTORY + " WHERE success AND version IS NOT NULL", String.class));
        var versions = Arrays.stream(info.all())
            .map(MigrationInfo::getVersion)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        MigrationVersion last = null;
        for (var version : versions) {
            if (!applied.contains(version.getVersion())) {
                break;
            }
            last = version;
        }
        return Optional.ofNullable(last);
    }
}
