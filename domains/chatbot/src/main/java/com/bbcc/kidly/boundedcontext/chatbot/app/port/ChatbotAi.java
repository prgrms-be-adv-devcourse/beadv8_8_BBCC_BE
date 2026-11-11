package com.bbcc.kidly.boundedcontext.chatbot.app.port;

import java.util.List;

/**
 * 챗봇이 쓰는 AI(임베딩 · 답 만들기). 키가 없거나 호출이 실패하면 {@link UnavailableException}을 던진다.
 */
public interface ChatbotAi {

    /**
     * 글마다 임베딩 벡터를 같은 순서로 준다.
     */
    List<float[]> embed(List<String> texts);

    /**
     * 지시문과 대화(마지막은 회원 질문)로 답을 만든다.
     */
    String answer(String instruction, List<Turn> conversation);

    record Turn(Speaker speaker, String content) {
    }

    enum Speaker {
        USER,
        ASSISTANT
    }

    final class UnavailableException extends RuntimeException {

        public UnavailableException(String message) {
            super(message);
        }

        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * 로그에 남길 원인 예외 이름. 원인 메시지에는 요청 본문이 들어 있을 수 있어 쓰지 않는다.
         */
        public String causeName() {
            return (getCause() == null ? this : getCause()).getClass().getSimpleName();
        }
    }
}
