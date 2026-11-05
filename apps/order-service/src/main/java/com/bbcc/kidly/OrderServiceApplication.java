package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 주문 서비스. 패키지가 com.bbcc.kidly라 의존한 주문 도메인(주문 · 장바구니 · 배송 · 조르기)과 common만 뜬다.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
