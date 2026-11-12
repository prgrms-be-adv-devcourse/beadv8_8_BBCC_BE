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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Spring AI 모델(OpenAI)로 임베딩 · 답을 만든다. 키가 없으면 모델 빈이 없어({@link ChatbotAiKeyApplicationListener})
 * 부를 때 쓸 수 없다고 알린다. 예외 메시지에 질문 · 답 · 문서 본문 · 도구 인자를 넣지 않는다.
 *
 * <p>chat 모델은 도구를 알리기만 하고 부르지 않는다. 모델이 도구를 부르면 여기서 실행해 결과를 붙여 다시 부른다.
 */
@Component
public class OpenAiChatbotAi implements ChatbotAi {

    // 회원이 화면에서 기다리고 부를 때마다 비용이 들어서, 도구를 이만큼 부른 뒤에는 도구 없이 불러 글로 답하게 한다
    static final int MAX_TOOL_ROUNDS = 3;

    private final ObjectProvider<ChatModel> chatModel;
    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

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
    public String answer(String instruction, List<Turn> conversation, List<Tool<?>> tools) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new UnavailableException("chat 모델이 없습니다(OpenAI 키 없음)");
        }
        List<Message> messages = messages(instruction, conversation);
        ChatOptions withTools = tools.isEmpty() ? null : withTools(model, tools);
        for (int round = 0; ; round++) {
            boolean toolsOffered = withTools != null && round < MAX_TOOL_ROUNDS;
            Prompt prompt = toolsOffered ? new Prompt(messages, withTools) : new Prompt(messages);
            ChatResponse response = call(model, prompt);
            if (!toolsOffered || !response.hasToolCalls()) {
                return text(response);
            }
            messages = executeTools(prompt, response);
        }
    }

    private static List<Message> messages(String instruction, List<Turn> conversation) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(instruction));
        for (Turn turn : conversation) {
            messages.add(switch (turn.speaker()) {
                case USER -> new UserMessage(turn.content());
                case ASSISTANT -> new AssistantMessage(turn.content());
            });
        }
        return messages;
    }

    // 프롬프트에 옵션을 주면 모델이 기본 옵션(모델 이름 · 추론 · 토큰 상한) 대신 그것만 쓰므로, 기본 옵션에 도구만 더한다
    private static ChatOptions withTools(ChatModel model, List<Tool<?>> tools) {
        if (!(model.getOptions().mutate() instanceof ToolCallingChatOptions.Builder<?> builder)) {
            throw new UnavailableException("chat 모델이 도구를 지원하지 않습니다");
        }
        return builder.toolCallbacks(tools.stream().map(OpenAiChatbotAi::callback).toList()).build();
    }

    private static <I> ToolCallback callback(Tool<I> tool) {
        return FunctionToolCallback.builder(tool.name(), tool.handler())
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .inputType(tool.inputType())
            .build();
    }

    private static ChatResponse call(ChatModel model, Prompt prompt) {
        ChatResponse response;
        try {
            response = model.call(prompt);
        } catch (RuntimeException e) {
            throw new UnavailableException("chat 호출 실패", e);
        }
        if (response == null || response.getResult() == null) {
            throw new UnavailableException("chat 답이 비었습니다");
        }
        return response;
    }

    private List<Message> executeTools(Prompt prompt, ChatResponse response) {
        try {
            return toolCallingManager.executeToolCalls(prompt, response).conversationHistory();
        } catch (RuntimeException e) {
            // 없는 도구 이름 · 읽을 수 없는 인자 등 모델이 잘못 부른 경우다
            throw new UnavailableException("도구 실행 실패", e);
        }
    }

    private static String text(ChatResponse response) {
        String text = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(text)) {
            throw new UnavailableException("chat 답이 비었습니다");
        }
        return text.strip();
    }
}
