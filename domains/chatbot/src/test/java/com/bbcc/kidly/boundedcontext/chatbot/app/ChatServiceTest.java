package com.bbcc.kidly.boundedcontext.chatbot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.chatbot.FakeChatModel;
import com.bbcc.kidly.boundedcontext.chatbot.FakeEmbeddingModel;
import com.bbcc.kidly.boundedcontext.chatbot.FakeMemberOrderHistory;
import com.bbcc.kidly.boundedcontext.chatbot.FakeProductSearcher;
import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService.ChatAnswer;
import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService.Source;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Speaker;
import com.bbcc.kidly.boundedcontext.chatbot.app.port.ChatbotAi.Turn;
import com.bbcc.kidly.boundedcontext.chatbot.domain.KnowledgeIndex;
import com.bbcc.kidly.boundedcontext.chatbot.out.ai.OpenAiChatbotAi;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.dto.MemberOrderSummary;
import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ChatServiceTest {

    private static final String RETURN_POLICY = "반품은 상품을 받은 날부터 7일 안에 마이페이지에서 신청할 수 있습니다.";
    private static final String SIZE_GUIDE = "키 110cm 아이는 110 사이즈를 고르세요. 성장이 빠르면 한 치수 크게 고르세요.";
    private static final String PRODUCT_QUESTION = "5살 여자아이 여름 원피스 3만원 이하로 찾아 주세요";
    private static final String ORDER_QUESTION = "어제 산 원피스 배송 언제 와요?";
    private static final Long MEMBER_ID = 424_242L;

    private final FakeChatModel chatModel = new FakeChatModel();
    private final FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel();
    private final FakeProductSearcher productSearcher = new FakeProductSearcher();
    private final FakeMemberOrderHistory orderHistory = new FakeMemberOrderHistory();
    private final KnowledgeIndexCache indexCache = mock(KnowledgeIndexCache.class);
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(indexCache, new OpenAiChatbotAi(provider(ChatModel.class, chatModel),
            provider(EmbeddingModel.class, embeddingModel)), new ChatbotRetrievalProperties(4, 0.3), productSearcher,
            orderHistory);
        given(indexCache.current()).willReturn(KnowledgeIndex.of(List.of(
            entry(1, 10, "교환 · 반품 정책", RETURN_POLICY),
            entry(2, 20, "사이즈 가이드", SIZE_GUIDE))));
    }

    @Test
    @DisplayName("가까운 조각만 지시문에 넣어 답을 받고, 그 문서를 출처로 준다. 상품을 찾지 않았으면 상품은 비어 있다")
    void answersFromMatchedChunks() {
        ChatAnswer answer = chatService.answer(MEMBER_ID, "반품은 받은 날부터 며칠 안에 신청할 수 있나요?", List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(answer.sources()).containsExactly(new Source(10L, "교환 · 반품 정책"));
        assertThat(answer.products()).isEmpty();
        String system = chatModel.lastPrompt().getInstructions().getFirst().getText();
        assertThat(system).contains("고객센터").contains(RETURN_POLICY).doesNotContain(SIZE_GUIDE);
    }

    @Test
    @DisplayName("이전 대화를 순서대로 보내고 마지막에 이번 질문을 붙인다")
    void sendsHistoryThenQuestion() {
        List<Turn> history = List.of(new Turn(Speaker.USER, "안녕하세요"), new Turn(Speaker.ASSISTANT, "무엇을 도와드릴까요?"));

        chatService.answer(MEMBER_ID, "반품은 며칠 안에 신청하나요?", history);

        List<Message> messages = chatModel.lastPrompt().getInstructions();
        assertThat(messages).extracting(Message::getMessageType).containsExactly(
            MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(messages.getLast().getText()).isEqualTo("반품은 며칠 안에 신청하나요?");
    }

    @Test
    @DisplayName("기준을 넘는 조각이 없어도 참고 문서 없이 AI를 불러 답하고, 출처는 비어 있다")
    void answersWithoutMatch() {
        ChatAnswer answer = chatService.answer(MEMBER_ID, "우주선 장난감 있어요?", List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(answer.sources()).isEmpty();
        String system = chatModel.lastPrompt().getInstructions().getFirst().getText();
        assertThat(system).doesNotContain(RETURN_POLICY).doesNotContain(SIZE_GUIDE);
    }

    @Test
    @DisplayName("문서가 하나도 없으면 질문을 임베딩하지 않고 AI만 부른다")
    void answersWithoutDocuments() {
        given(indexCache.current()).willReturn(KnowledgeIndex.of(List.of()));
        embeddingModel.failWith(new IllegalStateException("불리면 안 된다"));

        ChatAnswer answer = chatService.answer(MEMBER_ID, "반품은 며칠 안에 신청하나요?", List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    @DisplayName("모델이 상품 검색을 부르면 검색어 · 가격만 검색에 보내고, 결과를 모델에 준 뒤 찾은 상품을 답과 함께 준다")
    void answersWithSearchedProducts() {
        productSearcher.willReturn(List.of(hit(1), hit(2)));
        chatModel.callToolNext("searchProducts", "{\"keyword\":\"여아 여름 원피스\",\"maxPrice\":30000}");

        ChatAnswer answer = chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(answer.products()).containsExactly(hit(1), hit(2));
        assertThat(productSearcher.queries()).containsExactly(new ProductSearchQuery("여아 여름 원피스", null, 30_000L, 5));
        assertThat(toolResults(chatModel.lastPrompt().getInstructions()))
            .singleElement().asString().contains("\"productId\":1").contains("상품 1").contains("\"price\":10000");
    }

    @Test
    @DisplayName("여러 번 찾은 상품은 처음 찾은 순서대로 한 번씩, 최대 6개만 준다")
    void dedupesAndCapsProducts() {
        productSearcher.willReturn("원피스", LongStream.rangeClosed(1, 5).mapToObj(ChatServiceTest::hit).toList());
        productSearcher.willReturn("드레스", LongStream.rangeClosed(4, 8).mapToObj(ChatServiceTest::hit).toList());
        chatModel.callToolNext("searchProducts", "{\"keyword\":\"원피스\"}");
        chatModel.callToolNext("searchProducts", "{\"keyword\":\"드레스\"}");

        ChatAnswer answer = chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of());

        assertThat(answer.products()).extracting(ProductHit::productId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("상품 검색이 실패하면 모델에 검색할 수 없다고 알려 답하게 하고, 질문은 실패시키지 않는다")
    void searchFailureStillAnswers() {
        productSearcher.failWith(new IllegalStateException("상품 검색 호출 실패"));
        chatModel.callToolNext("searchProducts", "{\"keyword\":\"원피스\"}");

        ChatAnswer answer = chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(answer.products()).isEmpty();
        assertThat(toolResults(chatModel.lastPrompt().getInstructions()))
            .singleElement().asString().contains("상품 검색을 지금 할 수 없습니다");
    }

    @Test
    @DisplayName("모델이 도구를 계속 불러도 세 번까지만 실행하고, 그 뒤에는 도구 없이 불러 답을 받는다")
    void boundsToolRounds() {
        productSearcher.willReturn(List.of(hit(1)));
        for (int i = 0; i < 5; i++) {
            chatModel.callToolNext("searchProducts", "{\"keyword\":\"원피스\"}");
        }

        ChatAnswer answer = chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(productSearcher.queries()).hasSize(3);
        assertThat(chatModel.prompts()).hasSize(4);
    }

    @Test
    @DisplayName("모델이 보낸 인자 중 검색이 거절할 값은 고쳐 보낸다: 검색어 앞뒤 공백 · 50자 초과, 음수 가격, 최저가 > 최고가")
    void normalizesModelArguments() {
        chatModel.callToolNext("searchProducts",
            "{\"keyword\":\"  " + "가".repeat(60) + " \",\"minPrice\":-1,\"maxPrice\":20000}");
        chatModel.callToolNext("searchProducts", "{\"keyword\":\"원피스\",\"minPrice\":30000,\"maxPrice\":10000}");

        chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of());

        assertThat(productSearcher.queries()).containsExactly(
            new ProductSearchQuery("가".repeat(50), null, 20_000L, 5),
            new ProductSearchQuery("원피스", 10_000L, 30_000L, 5));
    }

    @Test
    @DisplayName("모델이 검색어 없이 부르면 검색하지 않고 검색어를 넣으라고 알린다")
    void blankKeywordIsNotSearched() {
        chatModel.callToolNext("searchProducts", "{\"keyword\":\" \"}");

        ChatAnswer answer = chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(productSearcher.queries()).isEmpty();
        assertThat(toolResults(chatModel.lastPrompt().getInstructions()))
            .singleElement().asString().contains("검색어를 넣어");
    }

    @Test
    @DisplayName("모델이 내 주문을 부르면 로그인한 회원으로 조회하고(모델이 보낸 회원 ID는 무시), 상태 · 택배사를 한국어로 바꿔 날짜와 함께 준다")
    void answersWithMyOrders() {
        orderHistory.willReturn(MEMBER_ID, List.of(shippingDressOrder()));
        orderHistory.willReturn(1L, List.of(new MemberOrderSummary(99L, LocalDateTime.now(), "PAID",
            List.of(new MemberOrderSummary.Item("남의 상품", null, 1, "ORDERED", null)))));
        chatModel.callToolNext("getMyRecentOrders", "{\"memberId\":1,\"limit\":3}");

        ChatAnswer answer = chatService.answer(MEMBER_ID, ORDER_QUESTION, List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(orderHistory.requests()).containsExactly(new FakeMemberOrderHistory.Request(MEMBER_ID, 3));
        assertThat(toolResults(chatModel.lastPrompt().getInstructions())).singleElement().asString()
            .contains("\"orderId\":31", "\"orderedAt\":\"2026-11-11 09:05\"", "\"status\":\"결제 완료\"",
                "여아 꽃무늬 원피스", "110 / 핑크", "\"quantity\":2", "\"status\":\"배송 중\"", "CJ대한통운",
                "\"shippedAt\":\"2026-11-11 15:20\"", "\"today\":")
            .doesNotContain("남의 상품", String.valueOf(MEMBER_ID), "memberId");
    }

    @Test
    @DisplayName("모르는 주문 · 상품 · 배송 상태와 택배사는 이름 그대로 모델에 준다")
    void passesUnknownNamesThrough() {
        orderHistory.willReturn(MEMBER_ID, List.of(new MemberOrderSummary(32L, LocalDateTime.of(2026, 11, 1, 10, 0),
            "PARTIALLY_REFUNDED", List.of(new MemberOrderSummary.Item("래시가드", null, 1, "RETURNING",
                new MemberOrderSummary.Delivery("RETURN_IN_TRANSIT", "KDEXP", null, null))))));
        chatModel.callToolNext("getMyRecentOrders", "{}");

        chatService.answer(MEMBER_ID, ORDER_QUESTION, List.of());

        assertThat(orderHistory.requests()).containsExactly(new FakeMemberOrderHistory.Request(MEMBER_ID, 5));
        assertThat(toolResults(chatModel.lastPrompt().getInstructions())).singleElement().asString()
            .contains("PARTIALLY_REFUNDED", "RETURNING", "RETURN_IN_TRANSIT", "KDEXP");
    }

    @Test
    @DisplayName("모델이 보낸 limit이 1~10 밖이면 범위 안으로 고쳐 조회한다")
    void clampsOrderLimit() {
        chatModel.callToolNext("getMyRecentOrders", "{\"limit\":50}");
        chatModel.callToolNext("getMyRecentOrders", "{\"limit\":0}");

        chatService.answer(MEMBER_ID, ORDER_QUESTION, List.of());

        assertThat(orderHistory.requests()).extracting(FakeMemberOrderHistory.Request::limit).containsExactly(10, 1);
    }

    @Test
    @DisplayName("주문 조회가 실패하면 모델에 볼 수 없다고 알려 답하게 하고, 질문은 실패시키지 않는다")
    void orderFailureStillAnswers() {
        orderHistory.failWith(new IllegalStateException("회원 주문 조회 호출 실패"));
        chatModel.callToolNext("getMyRecentOrders", "{}");

        ChatAnswer answer = chatService.answer(MEMBER_ID, ORDER_QUESTION, List.of());

        assertThat(answer.answer()).isEqualTo(FakeChatModel.ANSWER);
        assertThat(toolResults(chatModel.lastPrompt().getInstructions()))
            .singleElement().asString().contains("주문 정보를 지금 볼 수 없습니다");
    }

    @Test
    @DisplayName("지시문은 주문 사실을 도구 결과로만 말하고, 취소 · 반품은 직접 하지 않고 안내하라고 한다")
    void instructsOrderRules() {
        chatService.answer(MEMBER_ID, ORDER_QUESTION, List.of());

        String system = chatModel.lastPrompt().getInstructions().getFirst().getText();
        assertThat(system).contains("getMyRecentOrders", "짐작하지 마세요", "직접 처리할 수 없습니다", "주문 상세 화면");
    }

    @Test
    @DisplayName("모델이 없는 도구를 부르면 503 AI_UNAVAILABLE이다")
    void unknownToolIsUnavailable() {
        chatModel.callToolNext("deleteAllOrders", "{}");

        assertThatThrownBy(() -> chatService.answer(MEMBER_ID, PRODUCT_QUESTION, List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("임베딩이 실패하면 503 AI_UNAVAILABLE이다")
    void embeddingFailureIsUnavailable() {
        embeddingModel.failWith(new RuntimeException("timeout"));

        assertThatThrownBy(() -> chatService.answer(MEMBER_ID, "반품은 며칠 안에 신청하나요?", List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("답 만들기가 실패하면 503 AI_UNAVAILABLE이다")
    void chatFailureIsUnavailable() {
        chatModel.failWith(new RuntimeException("429"));

        assertThatThrownBy(() -> chatService.answer(MEMBER_ID, "반품은 며칠 안에 신청하나요?", List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    @Test
    @DisplayName("OpenAI 키가 없어 모델 빈이 없으면 503 AI_UNAVAILABLE이다")
    void noModelIsUnavailable() {
        ChatService withoutModels = new ChatService(indexCache, new OpenAiChatbotAi(
            new StaticListableBeanFactory().getBeanProvider(ChatModel.class),
            new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class)),
            new ChatbotRetrievalProperties(4, 0.3), productSearcher, orderHistory);

        assertThatThrownBy(() -> withoutModels.answer(MEMBER_ID, "반품은 며칠 안에 신청하나요?", List.of()))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ChatbotErrorCode.AI_UNAVAILABLE));
    }

    private static List<String> toolResults(List<Message> messages) {
        return messages.stream()
            .filter(ToolResponseMessage.class::isInstance)
            .flatMap(message -> ((ToolResponseMessage) message).getResponses().stream())
            .map(ToolResponseMessage.ToolResponse::responseData)
            .toList();
    }

    private static MemberOrderSummary shippingDressOrder() {
        return new MemberOrderSummary(31L, LocalDateTime.of(2026, 11, 11, 9, 5), "PAID", List.of(
            new MemberOrderSummary.Item("여아 꽃무늬 원피스", "110 / 핑크", 2, "ORDERED",
                new MemberOrderSummary.Delivery("SHIPPING", "CJ", LocalDateTime.of(2026, 11, 11, 15, 20), null))));
    }

    private static ProductHit hit(long id) {
        return new ProductHit(id, "상품 " + id, "키들리 상점", 10_000L * id, "https://cdn/" + id + ".jpg", false);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        return new StaticListableBeanFactory(Map.of("bean", bean)).getBeanProvider(type);
    }

    private static KnowledgeIndex.Entry entry(long chunkId, long documentId, String title, String content) {
        return new KnowledgeIndex.Entry(chunkId, documentId, title, content,
            FakeEmbeddingModel.vectorOf(title + "\n\n" + content));
    }
}
