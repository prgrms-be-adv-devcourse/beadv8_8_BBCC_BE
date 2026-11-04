package com.bbcc.kidly.member.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 자녀는 옛 자녀 서비스의 이력 테이블을 이어 쓴다. 회원 이력에 합치면 init 버전이 겹치고, dev · prod에 남은 자녀 이력을 모른다(#809)
// JPA 검증 전에 끝나도록 회원 Flyway 전략 안에서 먼저 돌린다
@Configuration
public class ChildProfileFlywayConfig {

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return memberFlyway -> {
            childProfileFlyway(dataSource).migrate();
            memberFlyway.migrate();
        };
    }

    // 이미 자녀 테이블이 있고 자녀 이력이 없는 DB(kidly-app이 먼저 만든 DB)는 자녀 outbox 버전으로 baseline을 잡는다
    public static Flyway childProfileFlyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/childprofile-migration")
            .defaultSchema("childprofile")
            .schemas("childprofile")
            .table("flyway_schema_history_childprofile")
            .baselineOnMigrate(true)
            .baselineVersion("202610300953")
            .outOfOrder(true)
            .load();
    }
}
