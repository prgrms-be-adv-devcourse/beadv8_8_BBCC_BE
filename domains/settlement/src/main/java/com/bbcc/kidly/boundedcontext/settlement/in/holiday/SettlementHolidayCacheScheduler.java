package com.bbcc.kidly.boundedcontext.settlement.in.holiday;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 메모리에 둔 공휴일을 하루 1회 비운다. 그 뒤 첫 조회가 그해 공휴일을 다시 받아 임시공휴일 지정이 하루 안에 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementHolidayCacheScheduler {

    private final SettlementBusinessCalendar businessCalendar;

    @Scheduled(cron = "${kidly.settlement.holiday.refresh-cron}", zone = "Asia/Seoul")
    public void refresh() {
        businessCalendar.clearCache();
        log.info("정산 공휴일 캐시를 비웠습니다");
    }
}
