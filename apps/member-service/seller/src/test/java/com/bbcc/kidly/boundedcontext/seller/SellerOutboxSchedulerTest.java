package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxRelay;
import com.bbcc.kidly.boundedcontext.seller.in.outbox.SellerOutboxScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SellerOutboxSchedulerTest {

    @Mock
    private SellerOutboxRelay outboxRelay;

    @Test
    @DisplayName("릴레이가 DB 오류 같은 예외로 끝나도 스케줄러 밖으로 던지지 않고, 로그에는 예외 종류만 남긴다")
    void relayKeepsRowValuesOutOfLogs(CapturedOutput output) {
        willThrow(new DataIntegrityViolationException("Failing row contains (판매자 1)"))
            .given(outboxRelay).relay();

        assertThatCode(new SellerOutboxScheduler(outboxRelay)::relay).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("WARN").contains("판매자 아웃박스 릴레이가 예외로 끝났습니다")
            .contains("DataIntegrityViolationException").doesNotContain("판매자 1");
    }

    @Test
    @DisplayName("정리가 예외로 끝나도 스케줄러 밖으로 던지지 않고, 로그에는 예외 종류만 남긴다")
    void cleanUpKeepsExceptionMessageOutOfLogs(CapturedOutput output) {
        willThrow(new DataIntegrityViolationException("Failing row contains (판매자 1)"))
            .given(outboxRelay).cleanUp();

        assertThatCode(new SellerOutboxScheduler(outboxRelay)::cleanUp).doesNotThrowAnyException();

        assertThat(output.getAll()).contains("WARN").contains("판매자 아웃박스 정리가 예외로 끝났습니다")
            .contains("DataIntegrityViolationException").doesNotContain("판매자 1");
    }
}
