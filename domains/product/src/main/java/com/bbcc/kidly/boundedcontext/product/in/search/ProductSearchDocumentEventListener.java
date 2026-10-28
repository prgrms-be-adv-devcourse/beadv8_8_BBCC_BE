package com.bbcc.kidly.boundedcontext.product.in.search;

import com.bbcc.kidly.boundedcontext.product.app.search.ProductSearchDocumentService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 상품이 보낸 상품 정보 변경됨을 상품 자신이 받아 검색용 표를 다시 만든다.
 * 주문에 {@code ProductEventListener}, 추천에 {@code RecommendProductEventListener}가 있어서,
 * 스프링 빈 이름이 겹치지 않게 검색용 표 이름을 붙인다.
 */
@Component
@RequiredArgsConstructor
public class ProductSearchDocumentEventListener {

    private final ProductSearchDocumentService documentService;

    @DomainEventListener
    public void on(ProductChangedEvent event) {
        documentService.refresh(event);
    }
}
