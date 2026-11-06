package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 금칙어 검사(이름 banned-words). 글자 · 숫자만 남기고 소문자로 맞춰 비교해 공백 · 특수문자 · 대소문자를 섞은 우회도 잡는다.
 * 사진은 보지 않는다. 외부를 부르지 않아 늘 PASSED 또는 FLAGGED(PROFANITY)다.
 */
public class BannedWordsModerator implements ModerationAdapter {

    private final List<String> words;

    public BannedWordsModerator(List<String> words) {
        this.words = words.stream().filter(Objects::nonNull).map(BannedWordsModerator::normalize)
            .filter(word -> !word.isEmpty()).distinct().toList();
    }

    @Override
    public String name() {
        return "banned-words";
    }

    @Override
    public Result moderate(Content content) {
        String text = normalize(content.text() == null ? "" : content.text());
        return words.stream().anyMatch(text::contains) ? Result.flagged(ModerationReason.PROFANITY) : Result.passed();
    }

    private static String normalize(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        value.toLowerCase(Locale.ROOT).codePoints()
            .filter(Character::isLetterOrDigit)
            .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
