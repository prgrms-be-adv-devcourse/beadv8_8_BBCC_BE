package com.bbcc.kidly;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDailyRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 정산 서비스(#729). 패키지가 com.bbcc.kidly라 의존한 정산 도메인과 common만 뜬다.
 */
@SpringBootApplication
public class SettlementServiceApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(SettlementServiceApplication.class, args);
        if (context.getEnvironment().matchesProfiles(SettlementDailyRunner.PROFILE)) {
            // 일회 실행(CronJob)은 정산이 끝나면 종료 코드로 끝낸다
            System.exit(SpringApplication.exit(context));
        }
    }
}
