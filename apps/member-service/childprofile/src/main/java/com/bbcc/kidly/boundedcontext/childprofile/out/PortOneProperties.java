package com.bbcc.kidly.boundedcontext.childprofile.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kidly.childprofile.portone")
public record PortOneProperties(String apiSecret) {
}
