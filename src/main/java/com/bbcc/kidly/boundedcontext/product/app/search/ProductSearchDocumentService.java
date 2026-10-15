package com.bbcc.kidly.boundedcontext.product.app.search;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocumentRepository;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductSearchDocumentService {

    private final ProductSearchDocumentRepository documentRepository;

    /**
     * 상품 정보 변경됨 이벤트의 값으로 검색용 표 행을 다시 만든다.
     * 표시 가격 · 품절 · attributes 계산은 엔티티가 하고, 저장된 행보다 늦게 일어난 이벤트일 때만 저장소가 덮어쓴다.
     */
    public void refresh(ProductChangedEvent event) {
        ProductSearchDocument document = ProductSearchDocument.create(toChangeInfo(event));
        documentRepository.saveIfNewer(document, LocalDateTime.now());
    }

    // 이벤트의 상품 등록 시각(createdAt)은 감사 칸과 겹치지 않게 productCreatedAt으로 옮긴다
    private ProductSearchDocument.ChangeInfo toChangeInfo(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new ProductSearchDocument.ChangeInfo(
            data.productId(),
            data.sellerId(),
            data.name(),
            data.categoryId(),
            data.status(),
            data.thumbnailUrl(),
            data.createdAt(),
            data.sizeFree(),
            toOptionInfos(data.options()),
            toAttributeEntryInfos(data.infoValues()),
            event.occurredAt());
    }

    // 목록이 null이면 그대로 넘겨, 값 검사를 엔티티가 맡도록 한다
    private List<ProductSearchDocument.OptionInfo> toOptionInfos(List<ProductChangedEvent.Option> options) {
        if (options == null) {
            return null;
        }
        List<ProductSearchDocument.OptionInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.Option option : options) {
            infos.add(new ProductSearchDocument.OptionInfo(
                option.price(), option.soldOut(), option.saleStatus(), toAttributeEntryInfos(option.values())));
        }
        return infos;
    }

    // 화면에 보이는 값(value)은 검색용 표가 쓰지 않으므로 옮기지 않는다
    private List<ProductSearchDocument.AttributeEntryInfo> toAttributeEntryInfos(
        List<ProductChangedEvent.AttributeEntry> entries) {
        if (entries == null) {
            return null;
        }
        List<ProductSearchDocument.AttributeEntryInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.AttributeEntry entry : entries) {
            infos.add(new ProductSearchDocument.AttributeEntryInfo(entry.code(), entry.valueId()));
        }
        return infos;
    }
}
