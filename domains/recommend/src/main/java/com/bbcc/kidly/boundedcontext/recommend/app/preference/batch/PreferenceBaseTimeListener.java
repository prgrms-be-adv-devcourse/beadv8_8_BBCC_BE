package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * 선호 Job의 배치 기준 시각을 Job ExecutionContext에 정한다.
 * 다시 실행하면 Spring Batch가 앞 실행의 Job ExecutionContext를 이어 주므로 첫 실행의 시각을 그대로 쓴다.
 * 다시 실행할 때 기준 시각이 바뀌면 이미 끝난 chunk의 줄이 정리 Step에서 지워지기 때문이다.
 */
public class PreferenceBaseTimeListener implements JobExecutionListener {

    public static final String BASE_TIME = "baseTime";
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Override
    public void beforeJob(JobExecution jobExecution) {
        ExecutionContext context = jobExecution.getExecutionContext();
        if (!context.containsKey(BASE_TIME)) {
            context.put(BASE_TIME, LocalDateTime.now(SERVICE_ZONE));
        }
    }
}
