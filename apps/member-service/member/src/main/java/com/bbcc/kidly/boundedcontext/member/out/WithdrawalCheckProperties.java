package com.bbcc.kidly.boundedcontext.member.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kidly.member.withdrawal-check")
public record WithdrawalCheckProperties(String orderUri, String settlementUri) {
}
