package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring AI 채팅 모델(OpenAI)에 규칙 계산 결과와 상품 사이즈를 보내 사이즈를 고르게 한다.
 * 모델 호출이 실패하면 예외를 그대로 던지고, 호출하는 쪽이 규칙 결과로 대신한다. 질문과 답의 본문은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiSizeChooser implements AiSizeChooser {

    private static final String SYSTEM_INSTRUCTION = """
        당신은 키즈 패션 쇼핑몰의 사이즈 추천 도우미입니다. 사용자 메시지는 JSON 질문 하나입니다.
        - 질문 JSON의 sizes에 있는 사이즈 이름 중 하나만 recommendedSize로 고르세요. 목록에 없는 사이즈는 쓰지 마세요.
        - ruleResult는 키와 체형 규칙으로 계산한 참고 답입니다. 따르지 않아도 됩니다.
        - child의 키, 몸무게, 입력 체형(bodyType), 판정 체형(judgedBodyType), 선호 핏(preferredFit)과
          sizes의 사이즈별 권장 키, 실측(cm)을 함께 보고 고르세요.
          judgedBodyType이 null이면 체형을 쓰지 않고 키만 보고 고른 것입니다.
        - referenceFit과 growthSpeed가 null이면 무시하세요.
        - fit은 ROOMY, PROPER, TIGHT 중 하나입니다.
        - reasons는 1개에서 3개입니다. type은 HEIGHT, BODY_TYPE, PREFERRED_FIT, MEASUREMENT, RULE_RESULT, GROWTH 중
          하나이고, detail은 보호자에게 그대로 보여 줄 짧은 한국어 존댓말 문장입니다("~요"로 끝냅니다).
          sourceId는 null입니다.
        """;

    private final ChatModel chatModel;
    private final JsonMapper jsonMapper;

    @Override
    public AiSizeAnswer choose(SizeAiQuestion question) {
        return ChatClient.create(chatModel).prompt()
            .system(SYSTEM_INSTRUCTION)
            .user(jsonMapper.writeValueAsString(question))
            .call()
            .entity(AiSizeAnswer.class);
    }
}
