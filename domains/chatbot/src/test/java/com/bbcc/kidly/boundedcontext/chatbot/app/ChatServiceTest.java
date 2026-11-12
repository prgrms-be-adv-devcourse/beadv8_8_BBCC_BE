package com.bbcc.kidly.boundedcontext.chatbot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.chatbot.FakeChatModel;
import com.bbcc.kidly.boundedcontext.chatbot.FakeEmbeddingModel;
import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService.ChatAnswer;
import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService.Source;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Speaker;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Turn;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeIndex;
import com.bbcc.kidly.boundedcontext.chatbot.out.ai.OpenAiChatbotAi;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ChatServiceTest {

    private static final String RETURN_POLICY = "반품은 상품을 받은 날부터 7일 안에 마이페이지에서 신청할 수 있습니다.";
    private static final String SIZE_GUIDE = "키 110cm 아이는 110 사이즈를 고르세요. 성장이 빠르면 한 치수 크게 고르세요.";

    private final FakeChatModel chatModel = new FakeChatModel();
    private final FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel();
    private final KnowledgeIndexCache indexCache = mock(KnowledgeIndexCache.class);
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(indexCache, new OpenAiChatbotAi(provider(ChatModel.class, chatModel),
            provider(EmbeddingModel.class, embeddingModel)), new ChatbotRetrievalProperties(4, 0.3));
        given(indexCache.current()).willReturn(KnowledgeIndex.of(List.of(
            entry(1, 10, "교환 · 반품 정책", RETURN_POLICY),
            entry(2, 20, "사이즈 가이드", SIZE_GUIDE))));
    }

    @Test
    @DisplayName("가까운 조각만 지시문에 넣어 답을 받고, 그 문서를 출처로 준다")
    void answersFromMatchedChunks() {
        ChatAnswer answer = chatService.answer("반품은 받은 날부터 며칠 안에 신청할 수 있나요?", List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(answer.sources()).containsExactly(new Source(10L, "교환 · 반품 정책"));
        String system = chatModel.lastPrompt().getInstructions().getFirst().getText();
        assertThat(system).contains("고객센터").contains(RETURN_POLICY).doesNotContain(SIZE_GUIDE);
    }

    @Test
    @DisplayName("이전 대화를 순서대로 보내고 마지막에 이번 질문을 붙인다")
    void sendsHistoryThenQuestion() {
        List<Turn> history = List.of(new Turn(Speaker.USER, "안녕하세요"), new Turn(Speaker.ASSISTANT, "무엇을 도와드릴까요?"));

        chatService.answer("반품은 며칠 안에 신청하나요?", history);

        List<Message> messages = chatModel.lastPrompt().getInstructions();
        assertThat(messages).extracting(Message::getMessageType).containsExactly(
            MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(messages.getLast().getText()).isEqualTo("반품은 며칠 안에 신청하나요?");
    }

    @Test
    @DisplayName("기준을 넘는 조각이 없으면 AI를 부르지 않고 정해진 답과 빈 출처를 준다")
    void unknownWithoutMatch() {
        ChatAnswer answer = chatService.answer("우주선 장난감 있어요?", List.of());

        assertThat(answer.answer()).isEqualTo(ChatService.UNKNOWN_ANSWER);
        assertThat(answer.sources()).isEmpty();
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    @DisplayName("문서가 하나도 없으면 질문을 임베딩하지도 않고 정해진 답을 준다")
    void unknownWithoutDocuments() {
        given(indexCache.current()).willReturn(KnowledgeIndex.of(List.of()));
        embeddingModel.failWith(new IllegalStateException("불리면 안 된다"));

        ChatAnswer answer = chatService.answer("반품은 며칠 안에 신청하나요?", List.of());

        assertThat(answer.answer()).isEqualTo(ChatService.UNKNOWN_ANSWER);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    @DisplayName("임베딩이 실패하면 503 AI_UNAVAILABLE이다")
    void embeddingFailureIsUnavailable() {
        embeddingModel.failWith(new RuntimeException("timeout"));

        assertThatThrownBy(() -> chatService.answer("반품은 며칠 안에 신청하나요?", List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("답 만들기가 실패하면 503 AI_UNAVAILABLE이다")
    void chatFailureIsUnavailable() {
        chatModel.failWith(new RuntimeException("429"));

        assertThatThrownBy(() -> chatService.answer("반품은 며칠 안에 신청하나요?", List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("OpenAI 키가 없어 모델 빈이 없으면 503 AI_UNAVAILABLE이다")
    void noModelIsUnavailable() {
        ChatService withoutModels = new ChatService(indexCache, new OpenAiChatbotAi(
            new StaticListableBeanFactory().getBeanProvider(ChatModel.class),
            new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class)),
            new ChatbotRetrievalProperties(4, 0.3));

        assertThatThrownBy(() -> withoutModels.answer("반품은 며칠 안에 신청하나요?", List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        return new StaticListableBeanFactory(Map.of("bean", bean)).getBeanProvider(type);
    }

    private static KnowledgeIndex.Entry entry(long chunkId, long documentId, String title, String content) {
        return new KnowledgeIndex.Entry(chunkId, documentId, title, content,
            FakeEmbeddingModel.vectorOf(title + "\n\n" + content));
    }
}
