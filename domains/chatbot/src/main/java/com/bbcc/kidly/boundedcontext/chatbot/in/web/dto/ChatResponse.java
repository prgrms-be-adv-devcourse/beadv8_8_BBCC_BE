package com.bbcc.kidly.boundedcontext.chatbot.in.web.dto;

import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService.ChatAnswer;
import java.util.List;

public record ChatResponse(String answer, List<SourceResponse> sources, List<ProductResponse> products) {

    public static ChatResponse from(ChatAnswer answer) {
        return new ChatResponse(answer.answer(),
            answer.sources().stream()
                .map(source -> new SourceResponse(source.documentId(), source.title()))
                .toList(),
            answer.products().stream()
                .map(product -> new ProductResponse(product.productId(), product.name(), product.businessName(),
                    product.price(), product.thumbnailUrl(), product.soldOut()))
                .toList());
    }

    public record SourceResponse(Long documentId, String title) {
    }

    /**
     * 상품 카드. 상호명 · 썸네일이 없으면 null.
     */
    public record ProductResponse(Long productId, String name, String businessName, Long price, String thumbnailUrl,
                                  boolean soldOut) {
    }
}
