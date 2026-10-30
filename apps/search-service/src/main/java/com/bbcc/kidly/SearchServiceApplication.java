package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 검색 서비스(#526). 패키지가 com.bbcc.kidly라 의존한 검색 도메인과 common만 뜬다.
 * DB를 쓰지 않으므로 DataSource · JPA 자동 설정과 common의 JPA 설정(JpaAuditingConfig · QuerydslConfig, EntityManager가 필요)을 뺀다.
 * common은 고치지 않고 이 서비스에서만 뺀다. @SpringBootApplication은 스캔 제외를 받지 않아 같은 구성을 직접 적는다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@ComponentScan(excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = "com\\.bbcc\\.kidly\\.global\\.config\\.(JpaAuditingConfig|QuerydslConfig)")})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
