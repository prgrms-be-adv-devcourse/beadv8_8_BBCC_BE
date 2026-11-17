package com.bbcc.kidly.boundedcontext.auth.in;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("kidly.security")
public record SecurityProperties(@DefaultValue("true") boolean secureCookie) {
}
