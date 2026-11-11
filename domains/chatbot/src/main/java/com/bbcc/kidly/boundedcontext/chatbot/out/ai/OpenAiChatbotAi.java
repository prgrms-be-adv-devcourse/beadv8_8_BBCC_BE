package com.bbcc.kidly.boundedcontext.chatbot.out.ai;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Spring AI 모델(OpenAI)로 임베딩 · 답을 만든다. 키가 없으면 모델 빈이 없어({@link ChatbotAiKeyApplicationListener})
 * 부를 때 쓸 수 없다고 알린다. 예외 메시지에 질문 · 답 · 문서 본문을 넣지 않는다.
 */
@Component
public class OpenAiChatbotAi implements ChatbotAi {

    private final ObjectProvider<ChatModel> chatModel;
    private final ObjectProvider<EmbeddingModel> embeddingModel;

    public OpenAiChatbotAi(ObjectProvider<ChatModel> chatModel, ObjectProvider<EmbeddingModel> embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingModel model = embeddingModel.getIfAvailable();
        if (model == null) {
            throw new UnavailableException("임베딩 모델이 없습니다(OpenAI 키 없음)");
        }
        List<float[]> vectors;
        try {
            vectors = model.embed(texts);
        } catch (RuntimeException e) {
            throw new UnavailableException("임베딩 호출 실패", e);
        }
        if (vectors.size() != texts.size()) {
            throw new UnavailableException("임베딩 개수가 글 수와 다릅니다: texts=" + texts.size() + ", embeddings="
                + vectors.size());
        }
        return vectors;
    }

    @Override
    public String answer(String instruction, List<Turn> conversation) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new UnavailableException("chat 모델이 없습니다(OpenAI 키 없음)");
        }
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(instruction));
        for (Turn turn : conversation) {
            messages.add(switch (turn.speaker()) {
                case USER -> new UserMessage(turn.content());
                case ASSISTANT -> new AssistantMessage(turn.content());
            });
        }
        ChatResponse response;
        try {
            response = model.call(new Prompt(messages));
        } catch (RuntimeException e) {
            throw new UnavailableException("chat 호출 실패", e);
        }
        String text = response == null || response.getResult() == null
            ? null
            : response.getResult().getOutput().getText();
        if (!StringUtils.hasText(text)) {
            throw new UnavailableException("chat 답이 비었습니다");
        }
        return text.strip();
    }
}
