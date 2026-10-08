package com.bbcc.kidly.global.entity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfig {

    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock();
    }
}
