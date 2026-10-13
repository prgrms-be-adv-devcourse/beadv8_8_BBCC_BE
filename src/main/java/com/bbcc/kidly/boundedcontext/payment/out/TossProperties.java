package com.bbcc.kidly.boundedcontext.payment.out;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스페이먼츠 API 설정. 시크릿 키는 환경 변수 TOSS_SECRET_KEY로만 받는다.
 * 테스트 키(test_sk_)를 쓰면 실제 돈이 오가지 않는다.
 */
@ConfigurationProperties("toss")
public record TossProperties(String secretKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
