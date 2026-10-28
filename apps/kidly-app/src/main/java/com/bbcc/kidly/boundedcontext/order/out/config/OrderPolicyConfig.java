package com.bbcc.kidly.boundedcontext.order.out.config;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 설정값을 도메인이 쓰는 Order.Policy로 바꿔 빈으로 둔다. app은 Order.Policy만 주입받는다.
 */
@Configuration
class OrderPolicyConfig {

    @Bean
    Order.Policy orderPolicy(OrderPolicyProperties properties) {
        return properties.toOrderPolicy();
    }
}
