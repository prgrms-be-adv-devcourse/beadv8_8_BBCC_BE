package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 상품 서비스(#791). 패키지가 com.bbcc.kidly라 의존한 상품 도메인과 common만 뜬다.
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
