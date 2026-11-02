package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 추천 서비스(#704). 패키지가 com.bbcc.kidly라 의존한 추천 도메인과 common만 뜬다.
 */
@SpringBootApplication
public class RecommendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendServiceApplication.class, args);
    }
}
