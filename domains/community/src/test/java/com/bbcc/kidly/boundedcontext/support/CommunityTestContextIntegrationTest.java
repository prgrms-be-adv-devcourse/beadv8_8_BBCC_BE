package com.bbcc.kidly.boundedcontext.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 커뮤니티 모듈 테스트 기반: 커뮤니티 테스트 앱이 커뮤니티 · common만으로 뜨고, 커뮤니티 마이그레이션이 community 스키마를 만든다.
 */
@IntegrationTest
class CommunityTestContextIntegrationTest {

    private static final String DOMAINS = "com.bbcc.kidly.boundedcontext.";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("커뮤니티 마이그레이션(community_schema)이 적용되어 community 스키마가 있다")
    void communitySchemaMigrated() {
        List<String> schemas = jdbcTemplate.queryForList(
            "select schema_name from information_schema.schemata where schema_name = 'community'", String.class);
        List<String> migrations = jdbcTemplate.queryForList(
            "select description from public.flyway_schema_history where success and version is not null", String.class);

        assertThat(schemas).containsExactly("community");
        assertThat(migrations).contains("community schema");
    }

    @Test
    @DisplayName("커뮤니티 밖 도메인의 빈이 하나도 없다(커뮤니티 · common · 스프링만)")
    void noOtherDomainBeans() {
        List<String> otherDomains = Arrays.stream(context.getBeanDefinitionNames())
            .map(name -> context.getType(name))
            .filter(Objects::nonNull)
            .map(Class::getName)
            .filter(type -> type.startsWith(DOMAINS) && !type.startsWith(DOMAINS + "review.")
                && !type.startsWith(DOMAINS + "favorite.") && !type.startsWith(DOMAINS + "support."))
            .toList();

        assertThat(otherDomains).isEmpty();
    }
}
