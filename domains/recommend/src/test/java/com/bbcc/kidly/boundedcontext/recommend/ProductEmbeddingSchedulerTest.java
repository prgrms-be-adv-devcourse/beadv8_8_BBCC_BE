package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.in.scheduler.ProductEmbeddingScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ProductEmbeddingSchedulerTest {

    @Mock
    private ProductEmbeddingService embeddingService;

    @Test
    @DisplayName("주기마다 낡은 상품 임베딩을 서비스에 맡긴다")
    void delegateToService() {
        new ProductEmbeddingScheduler(embeddingService).embedStaleProducts();

        verify(embeddingService).embedStaleProducts();
    }

    @Test
    @DisplayName("서비스가 예외를 던져도 스케줄러는 던지지 않고, 예외 메시지 · 스택 트레이스 없이 예외 종류만 오류 로그로 남긴다")
    void swallowExceptionAndLogOnlyExceptionType() {
        doThrow(new IllegalStateException("행 값 상품명: 데일리 맨투맨")).when(embeddingService).embedStaleProducts();
        ProductEmbeddingScheduler scheduler = new ProductEmbeddingScheduler(embeddingService);
        Logger schedulerLogger = (Logger) LoggerFactory.getLogger(ProductEmbeddingScheduler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        schedulerLogger.addAppender(logs);

        try {
            assertThatCode(scheduler::embedStaleProducts).doesNotThrowAnyException();
        } finally {
            schedulerLogger.detachAppender(logs);
        }

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                .contains("IllegalStateException")
                .doesNotContain("행 값", "데일리 맨투맨");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
