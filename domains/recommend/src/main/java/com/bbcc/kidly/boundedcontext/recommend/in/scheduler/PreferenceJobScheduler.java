package com.bbcc.kidly.boundedcontext.recommend.in.scheduler;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobResult;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 선호 Job을 실행한다. 테스트에서는 Job을 직접 실행해 확인하므로 스케줄러를 만들지 않는다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class PreferenceJobScheduler {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PreferenceJobService jobService;

    // 03:00에 시작하고, 실패하거나 배포로 놓친 실행을 같은 날 05:50까지 10분마다 다시 시도한다.
    // 이미 완료한 날은 서비스가 ALREADY_COMPLETED로 바로 끝낸다
    @Scheduled(cron = "${kidly.recommend.preference.cron:0 0/10 3-5 * * *}", zone = "Asia/Seoul")
    public void runPreferenceJob() {
        LocalDate baseDate = LocalDate.now(SERVICE_ZONE);
        try {
            PreferenceJobResult result = jobService.run(baseDate);
            logResult(baseDate, result);
        } catch (RuntimeException e) {
            // 예외 메시지와 스택 트레이스에는 DB 오류의 행 값이 들어갈 수 있어 예외 종류만 남긴다
            log.error("선호 Job 실행이 예외로 끝났습니다. baseDate={}, error={}", baseDate,
                e.getClass().getSimpleName());
        }
    }

    private void logResult(LocalDate baseDate, PreferenceJobResult result) {
        if (result == PreferenceJobResult.COMPLETED) {
            log.info("선호 Job을 끝냈습니다. baseDate={}, result={}", baseDate, result);
            return;
        }
        if (result == PreferenceJobResult.FAILED) {
            log.warn("선호 Job이 실패했습니다. baseDate={}, result={}", baseDate, result);
            return;
        }
        log.debug("선호 Job을 실행하지 않았습니다. baseDate={}, result={}", baseDate, result);
    }
}
