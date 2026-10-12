package com.bbcc.kidly.global.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

// 기본값은 이 클래스의 패키지만 찾으므로 도메인의 @ConfigurationProperties도 찾도록 루트 패키지를 지정한다
@Configuration
@ConfigurationPropertiesScan("com.bbcc.kidly")
public class PropertiesConfig {
}
