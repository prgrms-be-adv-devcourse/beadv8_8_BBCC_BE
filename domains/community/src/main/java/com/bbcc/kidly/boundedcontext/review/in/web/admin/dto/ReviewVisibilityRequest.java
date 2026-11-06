package com.bbcc.kidly.boundedcontext.review.in.web.admin.dto;

import tools.jackson.databind.JsonNode;

/**
 * 관리자 숨김 확정 · 다시 공개 요청. 값 확인은 서비스가 한다(HIDDEN · VISIBLE이 아니면 400 INVALID_VISIBILITY).
 *
 * @param visibility HIDDEN(숨김 확정) · VISIBLE(다시 공개). JSON 원래 타입으로 받아 문자열이 아니면 틀린 값으로 본다
 */
public record ReviewVisibilityRequest(JsonNode visibility) {

    public String value() {
        return visibility != null && visibility.isString() ? visibility.stringValue() : null;
    }
}
