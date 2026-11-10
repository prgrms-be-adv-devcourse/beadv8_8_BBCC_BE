package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * 선호 Job의 배치 기준 시각을 정한다.
 */
public class PreferenceBaseTimeListener implements JobExecutionListener {

    public static final String BASE_TIME = "baseTime";

    @Override
    public void beforeJob(JobExecution jobExecution) {
    }
}
