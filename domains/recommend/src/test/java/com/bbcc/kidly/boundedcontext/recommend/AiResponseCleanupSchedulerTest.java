package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseCleanupService;
import com.bbcc.kidly.boundedcontext.recommend.in.scheduler.AiResponseCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class AiResponseCleanupSchedulerTest {

    @Mock
    private AiResponseCleanupService cleanupService;

    @Test
    @DisplayName("실행할 때마다 보관 기간이 지난 AI 응답 지우기를 맡긴다")
    void deleteExpiredResponses() {
        new AiResponseCleanupScheduler(cleanupService).deleteExpiredResponses();

        verify(cleanupService).deleteExpiredResponses();
    }

    @Test
    @DisplayName("서비스가 예외를 던져도 스케줄러는 던지지 않고, 로그에 예외 메시지를 남기지 않는다")
    void swallowException() {
        doThrow(new QueryTimeoutException("Failing row contains (행 값)")).when(cleanupService)
            .deleteExpiredResponses();
        Logger schedulerLogger = (Logger) LoggerFactory.getLogger(AiResponseCleanupScheduler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        schedulerLogger.addAppender(logs);

        try {
            assertThatCode(() -> new AiResponseCleanupScheduler(cleanupService).deleteExpiredResponses())
                .doesNotThrowAnyException();
        } finally {
            schedulerLogger.detachAppender(logs);
        }

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("행 값");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    @DisplayName("매일 04:30 KST에 실행한다")
    void cronAndZone() throws NoSuchMethodException {
        Scheduled scheduled = AiResponseCleanupScheduler.class.getMethod("deleteExpiredResponses")
            .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${kidly.recommend.ai.response-cleanup-cron:0 30 4 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("test 프로필에서는 스케줄러를 만들지 않는다")
    void excludedInTestProfile() {
        Profile profile = AiResponseCleanupScheduler.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("!test");
    }
}
