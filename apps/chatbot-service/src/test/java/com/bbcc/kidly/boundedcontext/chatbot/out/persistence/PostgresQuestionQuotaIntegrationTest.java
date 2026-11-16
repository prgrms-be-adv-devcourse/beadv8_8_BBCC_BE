package com.bbcc.kidly.boundedcontext.chatbot.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class PostgresQuestionQuotaIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Autowired
    private PostgresQuestionQuota quota;

    @Test
    @DisplayName("상한까지는 세고 허락하며, 상한에 닿으면 더 세지 않고 거절한다. 날짜가 바뀌면 다시 센다")
    void countsUpToLimitPerDay() {
        assertThat(quota.tryAcquire(TODAY, 2)).isTrue();
        assertThat(quota.tryAcquire(TODAY, 2)).isTrue();
        assertThat(quota.tryAcquire(TODAY, 2)).isFalse();
        assertThat(quota.tryAcquire(TODAY, 3)).isTrue();

        assertThat(quota.tryAcquire(TODAY.plusDays(1), 2)).isTrue();
    }

    @Test
    @DisplayName("상한이 0이면 세지 않고 거절한다")
    void zeroLimitRejects() {
        assertThat(quota.tryAcquire(TODAY, 0)).isFalse();
        assertThat(quota.tryAcquire(TODAY, 1)).isTrue();
    }

    @Test
    @DisplayName("여럿이 동시에 세도 상한만큼만 허락한다")
    void concurrentAcquiresNeverExceedLimit() throws Exception {
        Callable<Boolean> acquire = () -> quota.tryAcquire(TODAY, 5);
        long allowed;
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 20).mapToObj(i -> executor.submit(acquire)).toList();
            allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                }
            }
        }

        assertThat(allowed).isEqualTo(5);
    }
}
