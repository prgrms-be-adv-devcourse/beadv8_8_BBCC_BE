package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiPicker;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiProduct;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionWeights;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI 채팅 모델(OpenAI)에게 후보 가운데서 함께 사면 좋은 상품을 고르게 한다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
public class OpenAiCompanionAiPicker implements CompanionAiPicker {

    // 프롬프트나 답의 모양을 바꾸면 이 답을 저장해 다시 쓰는 서비스의 프롬프트 버전을 올린다
    private static final String SYSTEM_PROMPT = """
        너는 키즈 패션 쇼핑몰의 코디 도우미다.
        보고 있는 상품과 함께 사면 좋은 상품을 후보 목록에서만 %d개 고른다.
        고른 순서가 추천 순서다. 후보 목록에 없는 상품 ID는 쓰지 않는다.
        이유는 보호자에게 보여 줄 40자 안의 한국어 한 문장으로 쓴다.
        """.formatted(CompanionWeights.PICK_COUNT);

    private static final String CATEGORY_SEPARATOR = " > ";

    private static final String FIELD_SEPARATOR = " | ";

    // 판매자가 쓴 글의 줄바꿈과 칸 구분 기호가 다른 줄이나 지시문처럼 읽히지 않게 공백으로 바꾼다
    private static final Pattern LINE_BREAK_OR_SEPARATOR = Pattern.compile("\\R|\\|");

    private final ChatClient chatClient;

    public OpenAiCompanionAiPicker(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public CompanionAiAnswer pick(CompanionAiRequest request) {
        return chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(userText(request))
            .call()
            .entity(CompanionAiAnswer.class);
    }

    private String userText(CompanionAiRequest request) {
        StringBuilder text = new StringBuilder();
        text.append("보고 있는 상품\n").append(line(request.viewed())).append("\n\n후보 상품\n");
        for (CompanionAiProduct candidate : request.candidates()) {
            text.append(line(candidate)).append('\n');
        }
        return text.toString();
    }

    private String line(CompanionAiProduct product) {
        String line = "상품 ID " + product.productId() + FIELD_SEPARATOR + flatten(product.name()) + FIELD_SEPARATOR
            + String.join(CATEGORY_SEPARATOR, product.categoryPathNames());
        if (product.brand() != null) {
            line += FIELD_SEPARATOR + flatten(product.brand());
        }
        return line;
    }

    private String flatten(String sellerText) {
        return LINE_BREAK_OR_SEPARATOR.matcher(sellerText).replaceAll(" ");
    }
}
