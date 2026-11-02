package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 자녀 프로필 서비스(#682). 패키지가 com.bbcc.kidly라 의존한 자녀 프로필 도메인과 common만 뜬다.
 */
@SpringBootApplication
public class ChildProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChildProfileServiceApplication.class, args);
    }
}
