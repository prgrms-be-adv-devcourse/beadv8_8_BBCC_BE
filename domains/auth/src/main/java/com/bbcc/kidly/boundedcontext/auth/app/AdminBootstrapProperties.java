package com.bbcc.kidly.boundedcontext.auth.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kidly.auth.admin.bootstrap")
public record AdminBootstrapProperties(String email, String password) {
}
