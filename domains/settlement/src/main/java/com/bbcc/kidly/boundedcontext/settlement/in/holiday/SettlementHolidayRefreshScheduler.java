package com.bbcc.kidly.boundedcontext.settlement.in.holiday;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementHolidayRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 서버가 매일 18~9시에 꺼져 새벽 갱신은 돌지 않는다. 기동 때 한 번, 낮에 하루 한 번 받는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementHolidayRefreshScheduler {

    private final SettlementHolidayRefreshService refreshService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${kidly.settlement.holiday.refresh-cron}", zone = "Asia/Seoul")
    public void refresh() {
        refreshService.refresh();
    }
}
