package com.bbcc.kidly.boundedcontext.review.in.mcp;

/**
 * 도구 인자가 틀렸다. 메시지는 정해진 문구라 그대로 도구 오류로 돌려준다(받은 값을 넣지 않는다).
 */
public class McpToolArgumentException extends RuntimeException {

    public McpToolArgumentException(String message) {
        super(message);
    }
}
