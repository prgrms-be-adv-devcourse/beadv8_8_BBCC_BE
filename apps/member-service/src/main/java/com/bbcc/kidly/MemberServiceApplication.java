package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 회원 서비스. 패키지가 com.bbcc.kidly라 의존한 회원 · 인증 · 판매자 도메인과 서비스 전용 지원 코드만 뜬다.
 */
@SpringBootApplication
public class MemberServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }
}
