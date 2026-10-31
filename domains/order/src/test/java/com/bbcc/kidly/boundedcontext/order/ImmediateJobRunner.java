package com.bbcc.kidly.boundedcontext.order;

import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import java.util.ArrayList;
import java.util.List;

/**
 * 단위 테스트용 작업 실행(#564): 잠금 없이 바로 실행하고 작업 이름을 기록한다. declining()은 늘 다른 서버가 돌고 있다고 보고 건너뛴다.
 */
public final class ImmediateJobRunner implements SingleServerJobRunner {

    private final boolean runs;
    private final List<String> jobNames = new ArrayList<>();

    public ImmediateJobRunner() {
        this(true);
    }

    private ImmediateJobRunner(boolean runs) {
        this.runs = runs;
    }

    public static ImmediateJobRunner declining() {
        return new ImmediateJobRunner(false);
    }

    @Override
    public boolean run(String jobName, Runnable job) {
        jobNames.add(jobName);
        if (runs) {
            job.run();
        }
        return runs;
    }

    public List<String> jobNames() {
        return List.copyOf(jobNames);
    }
}
