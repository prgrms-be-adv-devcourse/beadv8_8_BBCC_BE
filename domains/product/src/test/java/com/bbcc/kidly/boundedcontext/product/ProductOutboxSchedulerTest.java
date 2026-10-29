package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.in.outbox.ProductOutboxScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ProductOutboxSchedulerTest {

    @Mock
    private ProductOutboxRelayService relayService;

    @Mock
    private ProductOutboxCleanupService cleanupService;

    @Test
    @DisplayName("릴레이가 DB 오류 같은 예외로 끝나도 스케줄러 밖으로 던지지 않고, 로그에는 예외 종류만 남긴다")
    void relayKeepsRowValuesOutOfLogs(CapturedOutput output) {
        // DB 오류 메시지에는 행 값(본문)이 들어갈 수 있다
        willThrow(new DataIntegrityViolationException("Failing row contains (비밀 본문)"))
            .given(relayService).relay();
        ProductOutboxScheduler scheduler = new ProductOutboxScheduler(relayService, cleanupService);

        assertThatCode(scheduler::relay).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("WARN").contains("DataIntegrityViolationException")
            .doesNotContain("비밀 본문");
    }
}
