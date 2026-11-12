package com.bbcc.kidly.boundedcontext.chatbot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeChunkerTest {

    @Test
    @DisplayName("짧은 본문은 앞뒤 공백만 지운 한 조각이다")
    void shortTextIsOneChunk() {
        assertThat(KnowledgeChunker.split("\n  교환은 7일 안에 신청합니다.  \n")).containsExactly("교환은 7일 안에 신청합니다.");
    }

    @Test
    @DisplayName("공백뿐인 본문은 조각이 없다")
    void blankTextHasNoChunk() {
        assertThat(KnowledgeChunker.split(" \n\n \n")).isEmpty();
    }

    @Test
    @DisplayName("짧은 문단들은 빈 줄로 이어 한 조각에 묶는다")
    void packsShortParagraphs() {
        assertThat(KnowledgeChunker.split("첫 문단입니다.\n\n\n둘째 문단입니다.\n  \n셋째 문단입니다."))
            .containsExactly("첫 문단입니다.\n\n둘째 문단입니다.\n\n셋째 문단입니다.");
    }

    @Test
    @DisplayName("문단을 묶다 상한을 넘으면 새 조각을 만들고 앞 조각의 끝을 겹쳐 시작한다")
    void startsNewChunkWithOverlap() {
        String first = words("가", 60);
        String second = words("나", 60);
        String third = words("다", 60);

        List<String> chunks = KnowledgeChunker.split(String.join("\n\n", first, second, third));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo(first + "\n\n" + second);
        assertThat(chunks.get(1)).endsWith("\n\n" + third).startsWith(lastWordsOf(chunks.get(0)));
        assertThat(chunks).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(KnowledgeChunker.MAX_LENGTH));
    }

    @Test
    @DisplayName("상한보다 긴 한 문단은 글자 수로 자르고 이웃 조각끼리 겹친다")
    void splitsLongParagraphWithOverlap() {
        String paragraph = "0123456789".repeat(200);

        List<String> chunks = KnowledgeChunker.split(paragraph);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(KnowledgeChunker.MAX_LENGTH));
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1);
            assertThat(chunks.get(i)).startsWith(previous.substring(previous.length() - KnowledgeChunker.OVERLAP));
        }
        assertThat(chunks.getLast()).endsWith(paragraph.substring(paragraph.length() - 10));
    }

    @Test
    @DisplayName("모든 문단의 내용이 어느 한 조각에는 그대로 들어간다")
    void keepsEveryParagraph() {
        List<String> paragraphs = List.of(words("가", 50), words("나", 120), words("다", 30), words("라", 90));

        List<String> chunks = KnowledgeChunker.split(String.join("\n\n", paragraphs));

        assertThat(paragraphs).allSatisfy(paragraph ->
            assertThat(chunks).anySatisfy(chunk -> assertThat(chunk).contains(paragraph)));
    }

    // 5글자 낱말(예: "가가가가가")을 count개 공백으로 잇는다. 길이는 count * 6 - 1
    private static String words(String letter, int count) {
        return String.join(" ", Collections.nCopies(count, letter.repeat(5)));
    }

    private static String lastWordsOf(String chunk) {
        String tail = chunk.substring(chunk.length() - KnowledgeChunker.OVERLAP);
        return tail.substring(tail.indexOf(' ')).strip();
    }
}
