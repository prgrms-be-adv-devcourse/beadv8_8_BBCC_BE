package com.bbcc.kidly.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// @Async 메서드를 Boot 기본 실행기(applicationTaskExecutor)의 스레드에서 실행한다
@Configuration
@EnableAsync
public class AsyncConfig {
}
