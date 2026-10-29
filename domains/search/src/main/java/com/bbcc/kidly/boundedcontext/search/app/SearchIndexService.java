package com.bbcc.kidly.boundedcontext.search.app;

import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSellerRepository;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 상품 · 판매자 변경됨을 받아 검색 색인을 갱신한다.
 * 이벤트는 바뀐 뒤의 전체 값을 담으므로 덮어쓰기만 하고, 늦게 일어난 이벤트만 남기는 비교는 저장소가 한다.
 * DB를 쓰지 않으므로 트랜잭션을 열지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final SearchProductRepository productRepository;
    private final SearchSellerRepository sellerRepository;

    public void applyProductChanged(ProductChangedEvent event) {
        productRepository.saveIfNewer(SearchProduct.create(toChangeInfo(event)));
    }

    public void applySellerChanged(SellerChangedEvent event) {
        SellerChangedEvent.Data data = event.data();
        sellerRepository.saveIfNewer(SearchSeller.create(
            new SearchSeller.ChangeInfo(data.sellerId(), data.businessName(), event.occurredAt())));
    }

    // 이벤트의 상품 등록 시각(createdAt)은 이벤트 발생 시각과 헷갈리지 않게 productCreatedAt으로 옮긴다
    private SearchProduct.ChangeInfo toChangeInfo(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new SearchProduct.ChangeInfo(
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

    // 목록이 null이면 그대로 넘겨, 값 검사를 도메인이 맡도록 한다
    private List<SearchProduct.OptionInfo> toOptionInfos(List<ProductChangedEvent.Option> options) {
        if (options == null) {
            return null;
        }
        List<SearchProduct.OptionInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.Option option : options) {
            infos.add(new SearchProduct.OptionInfo(
                option.price(), option.soldOut(), option.saleStatus(), toAttributeEntryInfos(option.values())));
        }
        return infos;
    }

    // 화면에 보이는 값(value)은 지금 검색이 쓰지 않으므로 옮기지 않는다
    private List<SearchProduct.AttributeEntryInfo> toAttributeEntryInfos(
        List<ProductChangedEvent.AttributeEntry> entries) {
        if (entries == null) {
            return null;
        }
        List<SearchProduct.AttributeEntryInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.AttributeEntry entry : entries) {
            infos.add(new SearchProduct.AttributeEntryInfo(entry.code(), entry.valueId()));
        }
        return infos;
    }
}
