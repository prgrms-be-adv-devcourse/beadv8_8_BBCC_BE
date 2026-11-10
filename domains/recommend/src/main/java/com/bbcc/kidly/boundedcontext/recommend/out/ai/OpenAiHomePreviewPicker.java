package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewPicker;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI chat 모델(OpenAI)에게 홈 맛보기 후보 중 보여 줄 상품을 고르게 한다.
 * 프롬프트와 응답에 개인정보가 섞일 수 있으므로 로그나 예외 메시지에 그 본문을 넣지 않는다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
public class OpenAiHomePreviewPicker implements HomePreviewPicker {

    private static final String UNKNOWN = "알 수 없음";

    private static final String SYSTEM_INSTRUCTION = """
        당신은 키즈 패션 쇼핑몰의 상품 추천 도우미입니다.
        자녀의 정보와 후보 상품 목록을 보고, 이 자녀에게 맞는 상품을 후보 중에서 4개 고르세요.
        상품마다 고른 이유를 한 문장, 40자 안으로 쓰세요.
        후보 목록에 있는 상품 ID만 쓰세요.
        이유는 사용자에게 그대로 보이는 문구이므로 존댓말로 쓰고, 자녀의 이름을 지어내지 마세요.
        """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<HomePreviewAiAnswer> converter =
        new BeanOutputConverter<>(HomePreviewAiAnswer.class);

    public OpenAiHomePreviewPicker(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // ChatClient는 만들 때 모델의 기본 옵션을 읽는데 단위 테스트의 가짜 모델에는 그 값이 없어 ChatModel을 직접 부른다.
    // 응답 JSON 형식 안내는 변환기가 만든 문구를 사용자 글 끝에 붙인다
    @Override
    public HomePreviewAiAnswer pick(HomePreviewAiRequest request) {
        Prompt prompt = new Prompt(List.of(
            new SystemMessage(SYSTEM_INSTRUCTION),
            new UserMessage(userText(request) + "\n" + converter.getFormat())));
        ChatResponse response = chatModel.call(prompt);
        return converter.convert(response.getResult().getOutput().getText());
    }

    private String userText(HomePreviewAiRequest request) {
        HomePreviewAiRequest.Child child = request.child();
        StringBuilder text = new StringBuilder();
        text.append("자녀: 개월 수 ").append(valueOrUnknown(child.ageMonths()))
            .append(", 키(cm) ").append(valueOrUnknown(child.heightCm()))
            .append(", 몸무게(kg) ").append(valueOrUnknown(child.weightKg()))
            .append(", 체형 ").append(valueOrUnknown(child.bodyType()))
            .append(", 선호 핏 ").append(valueOrUnknown(child.preferredFit()))
            .append("\n\n후보 상품(ID | 이름 | 브랜드 | 카테고리 | 최저가 | 추천 사이즈):\n");
        for (HomePreviewAiRequest.Candidate candidate : request.candidates()) {
            text.append(candidate.productId())
                .append(" | ").append(candidate.name())
                .append(" | ").append(valueOrUnknown(candidate.brand()))
                .append(" | ").append(String.join(" > ", candidate.categoryPath()))
                .append(" | ").append(valueOrUnknown(candidate.minPrice()))
                .append(" | ").append(valueOrUnknown(candidate.recommendedSize()))
                .append('\n');
        }
        return text.toString();
    }

    // BigDecimal은 지수 표기가 나오지 않게 toPlainString으로, enum은 name으로 쓴다
    private String valueOrUnknown(Object value) {
        if (value == null) {
            return UNKNOWN;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        return value.toString();
    }
}
