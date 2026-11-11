package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobResult;
import com.bbcc.kidly.boundedcontext.recommend.app.preference.PreferenceJobService;
import com.bbcc.kidly.boundedcontext.recommend.in.scheduler.PreferenceJobScheduler;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class PreferenceJobSchedulerTest {

    @Mock
    private PreferenceJobService jobService;

    @Test
    @DisplayName("PREF-11 실행할 때마다 오늘(KST) 날짜를 기준일로 선호 Job을 맡긴다")
    void runWithTodayInSeoul() {
        when(jobService.run(any())).thenReturn(PreferenceJobResult.COMPLETED);

        new PreferenceJobScheduler(jobService).runPreferenceJob();

        verify(jobService).run(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("PREF-11 서비스가 예외를 던져도 스케줄러는 던지지 않는다")
    void swallowException() {
        doThrow(new IllegalStateException("행 값")).when(jobService).run(any());

        assertThatCode(() -> new PreferenceJobScheduler(jobService).runPreferenceJob())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PREF-11 매일 03:00 KST에 시작하고 같은 날 05:50까지 10분마다 다시 시도한다")
    void cronAndZone() throws NoSuchMethodException {
        Scheduled scheduled = PreferenceJobScheduler.class.getMethod("runPreferenceJob")
            .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${kidly.recommend.preference.cron:0 0/10 3-5 * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("PREF-11 test 프로필에서는 스케줄러를 만들지 않는다")
    void excludedInTestProfile() {
        Profile profile = PreferenceJobScheduler.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("!test");
    }
}
