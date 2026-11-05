package com.bbcc.kidly.boundedcontext.settlement.in.holiday;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 메모리에 둔 공휴일을 하루 1회 다시 받아 임시공휴일 지정이 하루 안에 반영되게 한다. 다시 받지 못하면 전 값을 유지한다(#808).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementHolidayCacheScheduler {

    private final SettlementBusinessCalendar businessCalendar;

    @Scheduled(cron = "${kidly.settlement.holiday.refresh-cron}", zone = "Asia/Seoul")
    public void refresh() {
        int failed = businessCalendar.refresh();
        if (failed > 0) {
            log.warn("정산 공휴일을 다시 받지 못한 해가 {}개입니다. 전에 받은 공휴일로 계속 판단합니다", failed);
            return;
        }
        log.info("정산 공휴일을 다시 받았습니다");
    }
}
