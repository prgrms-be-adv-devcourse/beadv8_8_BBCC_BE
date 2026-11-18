package com.bbcc.kidly;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDailyRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * 패키지가 com.bbcc.kidly라 의존한 정산 도메인과 common만 뜬다.
 */
@SpringBootApplication
public class SettlementServiceApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(SettlementServiceApplication.class, args);
        if (context.getEnvironment().matchesProfiles(SettlementDailyRunner.PROFILE)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
