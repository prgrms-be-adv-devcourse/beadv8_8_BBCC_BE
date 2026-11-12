package com.bbcc.kidly.boundedcontext.chatbot;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * OpenAI 없이 쓰는 chat 모델. 늘 {@link #ANSWER}로 답하고 마지막 요청을 남긴다. {@link #failWith}로 호출 실패를 흉내 낸다.
 */
public class FakeChatModel implements ChatModel {

    public static final String ANSWER = "테스트 답변입니다.";

    private volatile Prompt lastPrompt;
    private volatile RuntimeException failure;

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public Prompt lastPrompt() {
        return lastPrompt;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(ANSWER))));
    }
}
