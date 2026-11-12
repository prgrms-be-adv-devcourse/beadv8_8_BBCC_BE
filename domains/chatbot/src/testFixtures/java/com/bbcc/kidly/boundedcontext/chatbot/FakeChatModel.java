package com.bbcc.kidly.boundedcontext.chatbot;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * OpenAI 없이 쓰는 chat 모델. 늘 {@link #ANSWER}로 답하고 받은 요청을 남긴다. {@link #failWith}로 호출 실패를,
 * {@link #callToolNext}로 도구 호출을 흉내 낸다. 테스트가 끝나면 {@link #reset}으로 되돌린다.
 */
public class FakeChatModel implements ChatModel {

    public static final String ANSWER = "테스트 답변입니다.";

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final Queue<AssistantMessage.ToolCall> plannedToolCalls = new ConcurrentLinkedQueue<>();
    private final AtomicInteger toolCallIds = new AtomicInteger();
    private volatile RuntimeException failure;

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /**
     * 도구를 알린 다음 호출 한 번은 답 대신 이 도구를 부른다. 여러 번 정하면 차례로 쓴다. 도구를 알리지 않은 호출에는 쓰지 않는다.
     */
    public void callToolNext(String name, String argumentsJson) {
        plannedToolCalls.add(new AssistantMessage.ToolCall("call-" + toolCallIds.incrementAndGet(), "function", name,
            argumentsJson));
    }

    public void reset() {
        failure = null;
        plannedToolCalls.clear();
        prompts.clear();
    }

    public Prompt lastPrompt() {
        return prompts.isEmpty() ? null : prompts.getLast();
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
        if (offersTools(prompt) && !plannedToolCalls.isEmpty()) {
            AssistantMessage toolCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(plannedToolCalls.poll()))
                .build();
            return new ChatResponse(List.of(new Generation(toolCall)));
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(ANSWER))));
    }

    private static boolean offersTools(Prompt prompt) {
        return prompt.getOptions() instanceof ToolCallingChatOptions options && !options.getToolCallbacks().isEmpty();
    }
}
