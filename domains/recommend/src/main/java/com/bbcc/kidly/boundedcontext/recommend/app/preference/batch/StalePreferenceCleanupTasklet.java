package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * 이번 실행이 다시 쓰지 않은 대상(탈퇴한 회원, 삭제한 자녀, 기간 안에 활동이 없어진 대상)의 줄을 지운다.
 * 자녀 · 회원 Step이 모두 끝난 뒤에 돌아야 이번 실행이 쓴 줄을 지우지 않는다.
 */
@Slf4j
public class StalePreferenceCleanupTasklet implements Tasklet {

    private final PreferenceSnapshotStore snapshotStore;
    private final LocalDateTime baseTime;

    public StalePreferenceCleanupTasklet(PreferenceSnapshotStore snapshotStore, LocalDateTime baseTime) {
        this.snapshotStore = requireNonNull(snapshotStore, "snapshotStore는 필수입니다");
        this.baseTime = requireNonNull(baseTime, "baseTime은 필수입니다");
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deleted = snapshotStore.deleteCalculatedBefore(baseTime);
        log.info("선호 Job이 지난 계산 줄 {}개를 지웠습니다: baseTime={}", deleted, baseTime);
        return RepeatStatus.FINISHED;
    }
}
