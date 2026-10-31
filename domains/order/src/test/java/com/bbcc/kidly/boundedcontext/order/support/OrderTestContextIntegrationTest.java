package com.bbcc.kidly.boundedcontext.order.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.security.AccessTokenRevocations;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 주문 모듈 테스트 기반(#616): 주문 테스트 앱이 주문 · common만으로 뜨고, orders 스키마가 기준 SQL + 주문 마이그레이션으로
 * 만들어지며(엔티티와 맞지 않으면 ddl validate로 기동 실패), 다른 도메인이 구현하던 common 인터페이스는 테스트용 구현이 채운다.
 */
@IntegrationTest
class OrderTestContextIntegrationTest {

    private static final String DOMAINS = "com.bbcc.kidly.boundedcontext.";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccessTokenRevocations revocations;

    @Test
    @DisplayName("orders 스키마의 표 13개가 기준 SQL과 주문 마이그레이션(order_outbox)으로 만들어진다")
    void ordersSchemaMigrated() {
        List<String> tables = jdbcTemplate.queryForList(
            "select tablename from pg_tables where schemaname = 'orders' order by tablename", String.class);

        assertThat(tables).containsExactly("cart_item", "delivery", "order_event_failure", "order_event_failure_item",
            "order_item", "order_outbox", "order_status_history", "orders", "payment_recovery",
            "payment_recovery_item", "replica_child", "replica_product", "replica_seller");
    }

    @Test
    @DisplayName("주문 밖 도메인의 빈이 하나도 없다(주문 · common · 스프링만)")
    void noOtherDomainBeans() {
        List<String> otherDomains = Arrays.stream(context.getBeanDefinitionNames())
            .map(name -> context.getType(name))
            .filter(Objects::nonNull)
            .map(Class::getName)
            .filter(type -> type.startsWith(DOMAINS) && !type.startsWith(DOMAINS + "order."))
            .toList();

        assertThat(otherDomains).isEmpty();
    }

    @Test
    @DisplayName("Access Token 무효화 확인은 테스트용 구현(늘 무효화 안 됨)이 채운다")
    void revocationsAreTestImplementation() {
        assertThat(AopUtils.getTargetClass(revocations)).isEqualTo(NoRevokedAccessTokens.class);
    }
}
