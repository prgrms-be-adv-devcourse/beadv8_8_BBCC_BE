package com.bbcc.kidly.boundedcontext.review.out.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BannedWordsModeratorTest {

    private final BannedWordsModerator moderator = new BannedWordsModerator(List.of("나쁜말", "Bad Word"));

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"나쁜말", "이건 나쁜말이에요", "나 쁜 말", "나.쁜!말", "나_쁜-말", "BADWORD", "b a d w o r d",
        "bad-word!!", "앞에\n나쁜\t말"})
    @DisplayName("대소문자 · 공백 · 특수문자를 섞은 우회도 걸린다(이유 PROFANITY)")
    void flagsDisguisedWords(String text) {
        assertThat(moderator.moderate(new Content(text, List.of())))
            .isEqualTo(Result.flagged(ModerationReason.PROFANITY));
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"좋은 옷이에요", "나쁘지 않은 말", "badge word", "bad"})
    @DisplayName("금칙어가 없으면 통과")
    void passesCleanText(String text) {
        assertThat(moderator.moderate(new Content(text, List.of()))).isEqualTo(Result.passed());
    }

    @Test
    @DisplayName("사진은 보지 않는다(주소에 금칙어 글자가 있어도 통과), 금칙어 목록이 비면 늘 통과")
    void ignoresImagesAndEmptyList() {
        assertThat(moderator.moderate(new Content("좋아요", List.of("https://img.example/badword.jpg"))))
            .isEqualTo(Result.passed());
        assertThat(new BannedWordsModerator(List.of()).moderate(new Content("나쁜말", List.of())))
            .isEqualTo(Result.passed());
        assertThat(new BannedWordsModerator(List.of(" ", "!!")).moderate(new Content("아무 글", List.of())))
            .isEqualTo(Result.passed());
    }
}
