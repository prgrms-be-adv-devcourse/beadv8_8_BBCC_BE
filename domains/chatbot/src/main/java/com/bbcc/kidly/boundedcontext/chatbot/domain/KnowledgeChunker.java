package com.bbcc.kidly.boundedcontext.chatbot.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 문서 본문을 임베딩할 조각으로 나눈다. 문단(빈 줄) 단위로 {@value #MAX_LENGTH}자까지 묶고,
 * 조각이 바뀔 때 앞 조각 끝 {@value #OVERLAP}자 안팎을 다음 조각 앞에 겹쳐 문단 경계의 문맥이 끊기지 않게 한다.
 * 한 문단이 {@value #MAX_LENGTH}자를 넘으면 글자 수로 자르고 같은 길이만큼 겹친다.
 */
public final class KnowledgeChunker {

    public static final int MAX_LENGTH = 800;
    public static final int OVERLAP = 100;

    private static final String PARAGRAPH_BREAK = "\\n\\s*\\n";
    private static final String JOINER = "\n\n";

    private KnowledgeChunker() {
    }

    public static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        String current = "";
        for (String piece : pieces(text)) {
            if (current.isEmpty()) {
                current = piece;
            } else if (current.length() + JOINER.length() + piece.length() <= MAX_LENGTH) {
                current = current + JOINER + piece;
            } else {
                chunks.add(current);
                String overlap = tail(current);
                current = overlap.length() + JOINER.length() + piece.length() <= MAX_LENGTH
                    ? overlap + JOINER + piece
                    : piece;
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private static List<String> pieces(String text) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : text.strip().split(PARAGRAPH_BREAK)) {
            String stripped = paragraph.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.length() <= MAX_LENGTH) {
                pieces.add(stripped);
                continue;
            }
            int step = MAX_LENGTH - OVERLAP;
            for (int start = 0; start < stripped.length(); start += step) {
                int end = Math.min(start + MAX_LENGTH, stripped.length());
                pieces.add(stripped.substring(start, end).strip());
                if (end == stripped.length()) {
                    break;
                }
            }
        }
        return pieces;
    }

    // 낱말 중간에서 시작하지 않게, 끝 OVERLAP자 안의 첫 공백 뒤부터 쓴다
    private static String tail(String chunk) {
        String tail = chunk.substring(Math.max(0, chunk.length() - OVERLAP));
        int space = indexOfWhitespace(tail);
        return space < 0 ? tail.strip() : tail.substring(space).strip();
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
