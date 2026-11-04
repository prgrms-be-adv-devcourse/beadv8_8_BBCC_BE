package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 커뮤니티 서비스. 패키지가 com.bbcc.kidly라 의존한 커뮤니티 도메인(리뷰 · 찜)과 common만 뜬다.
 */
@SpringBootApplication
public class CommunityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
}
