package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
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

    // 상의 기준 옷 가슴단면 35cm와 상품 110의 가슴단면 34cm를 비교한 기준 핏을 담은 질문이다
    private static final SizeAiQuestion QUESTION_WITH_FIT = new SizeAiQuestion(
        QUESTION.child(), QUESTION.sizes(), QUESTION.ruleResult(),
        new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(new ReferenceFit.Item("CHEST_WIDTH",
            new BigDecimal("35.0"), List.of(new ReferenceFit.SizeDiff("110", new BigDecimal("34"),
                new BigDecimal("-1.0")))))),
        null);

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
            List.of(new AiSizeAnswer.Reason("HEIGHT", "키 115cm는 110의 권장 키 끝이에요.", null)), List.of()));
        assertThat(prompts).hasSize(1);
        Prompt sent = prompts.getFirst();
        assertThat(sent.getInstructions())
            .anySatisfy(message -> assertThat(message.getMessageType()).isEqualTo(MessageType.SYSTEM));
        assertThat(sent.getUserMessage().getText())
            .contains("\"judgedBodyType\":\"NORMAL\"", "\"size\":\"110\"", "\"ruleResult\"");
    }

    @Test
    @DisplayName("FIT-07 규칙 결과와 기준 핏 차이를 함께 보고 고르라고 지시하고, 기준 옷과 비교하지 말라는 지시는 하지 않는다")
    void instructToWeighReferenceFitWithRuleResult() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = prompt -> {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"recommendedSize":"110","fit":"TIGHT","reasons":[],"itemJudgements":[]}
                """))));
        };

        new OpenAiSizeChooser(model, JsonMapper.builder().build()).choose(QUESTION_WITH_FIT);

        String system = prompts.getFirst().getSystemMessage().getText();
        assertThat(system).contains("referenceFit", "ruleResult", "itemJudgements", "allowanceMinCm")
            .doesNotContain("실측은 받지 않습니다", "다른 옷과 비교해 보라는 말을 쓰지 마세요");
        assertThat(prompts.getFirst().getUserMessage().getText())
            .contains("\"comparedPart\":\"TOP\"", "\"diffCm\":-1.0");
    }

    @Test
    @DisplayName("FIT-09 모델이 준 항목별 판단(항목 · 차이 · 판단 · 여유 범위)을 읽는다")
    void readItemJudgements() {
        ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("""
            {"recommendedSize":"110","fit":"TIGHT","reasons":[],
             "itemJudgements":[{"code":"CHEST_WIDTH","diffCm":-1.0,"fit":"TIGHT",
                                "allowanceMinCm":0.0,"allowanceMaxCm":3.0}]}
            """))));

        AiSizeAnswer answer = new OpenAiSizeChooser(model, JsonMapper.builder().build()).choose(QUESTION_WITH_FIT);

        assertThat(answer.itemJudgements()).containsExactly(new AiSizeAnswer.Judgement(
            "CHEST_WIDTH", new BigDecimal("-1.0"), "TIGHT", new BigDecimal("0.0"), new BigDecimal("3.0")));
    }
}
