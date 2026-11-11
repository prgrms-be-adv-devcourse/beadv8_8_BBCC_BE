package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.out.ai.OpenAiSizeChooser;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

class OpenAiSizeChooserTest {

    private static final SizeAiQuestion QUESTION = new SizeAiQuestion(
        new SizeAiQuestion.Child(82, 115.0, 20.0, BodyType.NORMAL, PreferredFit.LOOSE, BodyType.NORMAL),
        List.of(new SizeAiQuestion.SizeLine("110", 105, 115, Map.of("TOTAL_LENGTH", new BigDecimal("42")))),
        new SizeAiQuestion.RuleResult("110", "TIGHT", List.of("110"), List.of()),
        null, null);

    @Test
    @DisplayName("시스템 지시와 질문 JSON을 모델에 보내고, 모델이 준 JSON을 사이즈 답으로 읽는다")
    void askModelAndReadAnswer() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"recommendedSize":"110","fit":"TIGHT",
                 "reasons":[{"type":"HEIGHT","detail":"키 115cm는 110의 권장 키 끝이에요.","sourceId":null}]}
                """))));
        };

        AiSizeAnswer answer = new OpenAiSizeChooser(model, JsonMapper.builder().build()).choose(QUESTION);

        assertThat(answer).isEqualTo(new AiSizeAnswer("110", "TIGHT",
            List.of(new AiSizeAnswer.Reason("HEIGHT", "키 115cm는 110의 권장 키 끝이에요.", null))));
        assertThat(prompts).hasSize(1);
        Prompt sent = prompts.getFirst();
        assertThat(sent.getInstructions())
            .anySatisfy(message -> assertThat(message.getMessageType()).isEqualTo(MessageType.SYSTEM));
        assertThat(sent.getUserMessage().getText())
            .contains("\"judgedBodyType\":\"NORMAL\"", "\"size\":\"110\"", "\"ruleResult\"");
    }

    @Test
    @DisplayName("기준 옷 정보를 받지 않으므로, 가지고 계신 옷과 비교해 보라는 말 대신 상품 실측으로 근거를 쓰라고 지시한다")
    void instructMeasurementReasonWithoutOwnedGarment() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"recommendedSize":"110","fit":"TIGHT","reasons":[]}
                """))));
        };

        new OpenAiSizeChooser(model, JsonMapper.builder().build()).choose(QUESTION);

        String system = prompts.getFirst().getSystemMessage().getText();
        assertThat(system).contains("가지고 계신 옷", "상품 실측");
    }
}
