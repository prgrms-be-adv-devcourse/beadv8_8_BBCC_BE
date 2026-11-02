package com.bbcc.kidly.boundedcontext.support;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 커뮤니티 통합 · 기능 테스트가 띄우는 앱: 커뮤니티(리뷰 · 찜 컨텍스트)와 common(global)만 스캔한다. 이 모듈 테스트 클래스패스에는
 * 다른 도메인 모듈이 없으므로(kidly.domain이 의존을 막는다) 다른 도메인 빈은 원래 없다. 엔티티 · 리포지토리는 두 컨텍스트 패키지에서
 * 찾는다(직접 붙인 @AutoConfigurationPackage가 이 클래스 패키지 기본값을 대신한다). 테스트 지원(boundedcontext.support)도 스캔한다.
 */
@SpringBootApplication(scanBasePackages = {"com.bbcc.kidly.boundedcontext.review",
    "com.bbcc.kidly.boundedcontext.favorite", "com.bbcc.kidly.global", "com.bbcc.kidly.boundedcontext.support"})
@AutoConfigurationPackage(basePackages = {"com.bbcc.kidly.boundedcontext.review",
    "com.bbcc.kidly.boundedcontext.favorite"})
public class CommunityTestApplication {
}
