package com.bbcc.kidly.boundedcontext.order;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 주문 통합 · 기능 테스트가 띄우는 앱(#616): 주문과 common(global)만 스캔한다. 이 모듈 테스트 클래스패스에는 다른 도메인 모듈이
 * 없으므로(kidly.domain이 의존을 막는다) 다른 도메인 빈은 원래 없다. 엔티티 · 리포지토리는 이 클래스의 패키지(주문)에서 찾는다.
 * 다른 도메인이 구현하던 common 인터페이스(AccessTokenRevocations)는 support의 테스트용 구현이 채운다.
 * 주문 모듈의 장바구니 · 배송 컨텍스트(#617)도 같이 스캔하고, 세 컨텍스트를 엔티티 · 리포지토리 패키지로 둔다
 * (직접 붙인 @AutoConfigurationPackage가 이 클래스 패키지 기본값을 대신하므로 주문도 적는다).
 */
@SpringBootApplication(scanBasePackages = {"com.bbcc.kidly.boundedcontext.order", "com.bbcc.kidly.boundedcontext.cart",
    "com.bbcc.kidly.boundedcontext.delivery", "com.bbcc.kidly.global"})
@AutoConfigurationPackage(basePackages = {"com.bbcc.kidly.boundedcontext.order", "com.bbcc.kidly.boundedcontext.cart",
    "com.bbcc.kidly.boundedcontext.delivery"})
public class OrderTestApplication {
}
