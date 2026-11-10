package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.recommend.out.ai.OpenAiHomePreviewPicker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 홈 맛보기 AI 호출기는 Spring AI가 chat 모델 빈을 만드는 조건과 같을 때만 뜬다.
 * 키가 있는데 빈이 뜨지 않으면 홈 맛보기가 오류 없이 늘 규칙 결과가 되기 때문이다.
 */
class HomePreviewPickerBeanConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(OpenAiHomePreviewPicker.class)
        .withBean(ChatModel.class, () -> mock(ChatModel.class));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "openai")
    @DisplayName("chat 모델 설정이 없거나 openai이면 홈 맛보기 AI 호출기 빈이 뜬다")
    void createPickerWhenChatModelIsOpenAi(String chatModel) {
        ApplicationContextRunner configured = chatModel == null
            ? runner
            : runner.withPropertyValues("spring.ai.model.chat=" + chatModel);

        configured.run(context -> assertThat(context).hasSingleBean(OpenAiHomePreviewPicker.class));
    }

    @Test
    @DisplayName("chat 모델 설정이 none이면 홈 맛보기 AI 호출기 빈이 뜨지 않는다")
    void skipPickerWhenChatModelIsNone() {
        runner.withPropertyValues("spring.ai.model.chat=none")
            .run(context -> assertThat(context).doesNotHaveBean(OpenAiHomePreviewPicker.class));
    }
}
