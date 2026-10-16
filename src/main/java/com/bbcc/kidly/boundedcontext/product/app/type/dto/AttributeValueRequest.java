package com.bbcc.kidly.boundedcontext.product.app.type.dto;

/**
 * 항목에 더할 목록 값 하나다.
 *
 * @param colorCode 팔레트 값일 때만 보낸다
 */
public record AttributeValueRequest(String name, String colorCode) {
}
