package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.out.ai.OpenAiHomePreviewPicker;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 홈 맛보기 chat 어댑터가 자녀 값과 후보를 프롬프트에 넣고, 모델이 돌려준 JSON을 AI 답으로 읽는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiHomePreviewPickerTest {

    private static final HomePreviewAiRequest REQUEST = new HomePreviewAiRequest(
        new HomePreviewAiRequest.Child(
            48, new BigDecimal("105.5"), new BigDecimal("17.2"), BodyType.NORMAL, PreferredFit.REGULAR),
        List.of(
            new HomePreviewAiRequest.Candidate(301L, "줄무늬 티셔츠", "키들리", List.of("의류", "상의"), 15_000L, "110"),
            new HomePreviewAiRequest.Candidate(302L, "데님 바지", "꼬마", List.of("의류", "바지"), 25_000L, "110")));

    @Mock
    private ChatModel chatModel;

    @Test
    @DisplayName("모델이 돌려준 JSON의 상품 ID와 이유를 AI 답으로 읽는다")
    void readAnswerFromModelJson() {
        given(chatModel.call(any(Prompt.class))).willReturn(response(
            "{\"picks\":[{\"productId\":302,\"reason\":\"키에 맞는 바지예요\"},"
                + "{\"productId\":301,\"reason\":\"자주 본 상의예요\"}]}"));

        HomePreviewAiAnswer answer = new OpenAiHomePreviewPicker(chatModel).pick(REQUEST);

        assertThat(answer.picks())
            .extracting(HomePreviewAiAnswer.Pick::productId, HomePreviewAiAnswer.Pick::reason)
            .containsExactly(tuple(302L, "키에 맞는 바지예요"), tuple(301L, "자주 본 상의예요"));
    }

    @Test
    @DisplayName("프롬프트에 자녀의 개월 수 · 키 · 몸무게 · 체형 · 선호 핏과 후보의 ID · 이름 · 브랜드 · 카테고리 경로 · 가격 · 추천 사이즈를 넣는다")
    void promptHasChildValuesAndCandidates() {
        given(chatModel.call(any(Prompt.class))).willReturn(response("{\"picks\":[]}"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);

        new OpenAiHomePreviewPicker(chatModel).pick(REQUEST);

        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
            .contains("48", "105.5", "17.2", "NORMAL", "REGULAR")
            .contains("301", "줄무늬 티셔츠", "키들리", "의류 > 상의", "15000", "110")
            .contains("302", "데님 바지", "꼬마", "의류 > 바지", "25000");
    }

    private static ChatResponse response(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }
}
