package com.bbcc.kidly.boundedcontext.review.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator;
import com.bbcc.kidly.boundedcontext.review.out.moderation.ChainedContentModerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 검사기 설정(kidly.community.moderation.*): 목록 순서대로 이어 붙이고, 키가 없으면 AI를 빼고, 모르는 이름 · 빈 목록이면 키 이름이 나오는
 * 오류로 기동에 실패한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class CommunityModerationConfigTest {

    private static final String PREFIX = "kidly.community.moderation.";
    private static final String KEY = "gsk_SECRET_CONFIG_TEST";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CommunityModerationConfig.class)
        .withPropertyValues(
            PREFIX + "moderators[0]=banned-words",
            PREFIX + "moderators[1]=ai",
            PREFIX + "banned-words[0]=나쁜말",
            PREFIX + "ai.base-url=http://127.0.0.1:9/openai/v1",
            PREFIX + "ai.model=meta-llama/llama-guard-4-12b",
            PREFIX + "ai.timeout=PT10S");

    @Test
    @DisplayName("키가 있으면 설정 순서대로 금칙어 → AI를 이어 붙인다")
    void chainsInOrder() {
        runner.withPropertyValues(PREFIX + "ai.api-key=" + KEY)
            .run(context -> assertThat(((ChainedContentModerator) context.getBean(ContentModerator.class)).names())
                .containsExactly("banned-words", "ai"));
    }

    @Test
    @DisplayName("키가 없으면 AI를 빼고 금칙어만 쓰며, 기동 때 'AI 검사 꺼짐(키 없음)' 한 줄을 값 없이 남긴다")
    void skipsAiWithoutKey(CapturedOutput output) {
        runner.withPropertyValues(PREFIX + "ai.api-key=")
            .run(context -> assertThat(((ChainedContentModerator) context.getBean(ContentModerator.class)).names())
                .containsExactly("banned-words"));
        assertThat(output.getAll()).containsOnlyOnce("AI 검사 꺼짐(키 없음)");
    }

    @Test
    @DisplayName("키 값은 로그에 남지 않는다")
    void neverLogsKey(CapturedOutput output) {
        runner.withPropertyValues(PREFIX + "ai.api-key=" + KEY).run(context -> assertThat(context).hasNotFailed());
        assertThat(output.getAll()).doesNotContain(KEY);
    }

    @Test
    @DisplayName("모르는 검사기 이름이면 moderators 키가 나오는 오류로 기동에 실패한다")
    void failsOnUnknownName() {
        runner.withPropertyValues(PREFIX + "moderators[1]=gemini")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(PREFIX + "moderators")
                    .hasStackTraceContaining("gemini");
            });
    }

    @Test
    @DisplayName("키가 없어 AI를 빼고 나니 쓸 검사기가 없으면(moderators: [ai]) 조용히 모두 통과시키지 않고 기동에 실패한다")
    void failsWhenNoModeratorLeft() {
        new ApplicationContextRunner().withUserConfiguration(CommunityModerationConfig.class)
            .withPropertyValues(PREFIX + "moderators[0]=ai", PREFIX + "ai.api-key=",
                PREFIX + "ai.base-url=http://127.0.0.1:9/openai/v1", PREFIX + "ai.model=m", PREFIX + "ai.timeout=PT1S")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(PREFIX + "moderators");
            });
    }

    @Test
    @DisplayName("검사기 목록이 비면 키 이름이 나오는 오류로 기동에 실패한다")
    void failsOnEmptyList() {
        new ApplicationContextRunner().withUserConfiguration(CommunityModerationConfig.class)
            .withPropertyValues(PREFIX + "ai.base-url=http://127.0.0.1:9", PREFIX + "ai.model=m",
                PREFIX + "ai.timeout=PT1S")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("kidly.community.moderation");
            });
    }

    @Test
    @DisplayName("AI 주소 · 모델 · 시간이 비거나 틀리면 기동에 실패한다")
    void failsOnInvalidAiSettings() {
        runner.withPropertyValues(PREFIX + "ai.base-url=").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(PREFIX + "ai.model=").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(PREFIX + "ai.timeout=PT0S").run(context -> assertThat(context).hasFailed());
    }
}
