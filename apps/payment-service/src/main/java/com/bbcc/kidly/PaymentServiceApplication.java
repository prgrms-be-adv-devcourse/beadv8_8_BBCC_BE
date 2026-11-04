package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 결제 서비스(#525). 패키지가 com.bbcc.kidly라 의존한 결제 도메인과 common만 뜬다.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
