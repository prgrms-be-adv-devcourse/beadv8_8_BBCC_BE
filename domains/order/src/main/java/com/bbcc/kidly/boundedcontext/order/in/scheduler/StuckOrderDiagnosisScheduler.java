package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import com.bbcc.kidly.boundedcontext.order.app.stuck.StuckOrderDiagnosisService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 멈춘 주문 AI 진단(#926) 주기 작업. 주기는 kidly.order.ai.interval(기본값은 order.yml), 첫 실행도 기동 뒤 주기만큼 지나서다.
 * 스케줄러 스레드(아웃박스 릴레이 등과 같이 씀)를 AI 응답 대기로 막지 않게, 실행은 이 작업 전용 스레드 하나에 넘기고 바로 돌아온다
 * (비동기 · 주문 처리 트랜잭션과 분리). 앞 실행이 아직 돌면 이번 주기는 건너뛴다.
 * 전용 스레드에서는 한 번에 한 서버에서만(#564, 잠금 kidly.order.stuck-diagnosis) 새로 멈춘 대상을 진단한다.
 * 실패는 WARN 한 줄(예외 종류만)만 남기고 다음 주기에 다시 본다.
 */
@Slf4j
@Component
public class StuckOrderDiagnosisScheduler {

    static final String DIAGNOSIS_JOB = "stuck-diagnosis";

    private final StuckOrderDiagnosisService diagnosisService;
    private final SingleServerJobRunner jobRunner;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("order-stuck-diagnosis").daemon(true).factory());
    private final AtomicBoolean running = new AtomicBoolean();

    public StuckOrderDiagnosisScheduler(StuckOrderDiagnosisService diagnosisService,
        SingleServerJobRunner jobRunner,
        // 주기 값이 Duration으로 읽히지 않으면 @Scheduled 등록 전에 키 이름이 나오는 오류로 실패하게 주입한다
        @Value("${kidly.order.ai.interval}") Duration interval) {
        this.diagnosisService = diagnosisService;
        this.jobRunner = jobRunner;
    }

    // 기동 직후에는 부르지 않는다(첫 실행도 주기만큼 뒤). 테스트 컨텍스트에서도 다른 테스트에 끼어들지 않는다
    @Scheduled(initialDelayString = "${kidly.order.ai.interval}", fixedDelayString = "${kidly.order.ai.interval}")
    public void diagnose() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.execute(this::runOnWorker);
        } catch (RejectedExecutionException e) {
            running.set(false);
        }
    }

    /**
     * 전용 스레드에서 도는 한 실행(테스트가 직접 부를 수 있게 둔다).
     */
    void runOnWorker() {
        try {
            StuckOrderDiagnosisService.Run[] run = new StuckOrderDiagnosisService.Run[1];
            if (jobRunner.run(DIAGNOSIS_JOB, () -> run[0] = diagnosisService.diagnoseNew(LocalDateTime.now()))
                && run[0] != null && run[0].requested() > 0) {
                log.info("멈춘 주문 진단: requested={} diagnosed={} failed={} dailyLimitReached={}",
                    run[0].requested(), run[0].diagnosed(), run[0].failed(), run[0].limitReached());
            }
        } catch (RuntimeException e) {
            log.warn("멈춘 주문 진단 실행 실패: error={}", e.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        worker.shutdownNow();
        worker.awaitTermination(5, TimeUnit.SECONDS);
    }
}
