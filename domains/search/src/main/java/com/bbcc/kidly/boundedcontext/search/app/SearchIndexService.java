package com.bbcc.kidly.boundedcontext.search.app;

import com.bbcc.kidly.boundedcontext.search.app.port.TextEmbedder;
import com.bbcc.kidly.boundedcontext.search.domain.EmbeddingRefillTarget;
import com.bbcc.kidly.boundedcontext.search.domain.ProductEmbedding;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import com.bbcc.kidly.boundedcontext.search.domain.SearchSellerRepository;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 상품 · 판매자 변경됨을 받아 검색 색인을 갱신한다.
 * 이벤트는 바뀐 뒤의 전체 값을 담으므로 덮어쓰기만 하고, 늦게 일어난 이벤트만 남기는 비교는 저장소가 한다.
 * 상품 문서에는 의미 검색 벡터를 함께 저장한다. 문장(상품명 · 브랜드 · 설명)이 그대로면 저장된 벡터를 다시 쓰고,
 * 임베딩 API를 쓸 수 없으면 문장과 함께 벡터 없이 저장한다. 그 상품은 키워드로만 찾다가, 주기 작업
 * (refillMissingEmbeddings)이나 다음 변경 이벤트가 벡터를 다시 만든다.
 * DB를 쓰지 않으므로 트랜잭션을 열지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final SearchProductRepository productRepository;
    private final SearchSellerRepository sellerRepository;
    private final TextEmbedder textEmbedder;

    // 한 번에 다시 만드는 상품 수다. 임베딩 API를 상품마다 한 번씩 부르므로 한 주기를 짧게 묶어 둔다
    static final int REFILL_BATCH_SIZE = 20;

    // 직전 주기에 찾은 마지막 상품 ID다. 다음 주기는 그 뒤부터 찾고, 끝까지 가면(찾은 수가 한 주기보다 적으면) 처음으로 돌아간다.
    // 계속 실패하는 상품(API가 그 문장을 거절 등)이 앞자리를 차지해 뒤 상품이 채워지지 않는 것을 막는다.
    // 인스턴스마다 따로 두고, 다시 뜨면 처음부터 찾는다. 주기 작업은 겹쳐 돌지 않는다(fixedDelay)
    private volatile Long refillCursor;

    public void applyProductChanged(ProductChangedEvent event) {
        SearchProduct product = SearchProduct.create(toChangeInfo(event));
        product.attachEmbedding(embeddingOf(product));
        productRepository.saveIfNewer(product);
    }

    // 재고 · 가격만 바뀐 이벤트가 많아, 문장이 같으면 저장된 벡터를 쓰고 API를 부르지 않는다
    private ProductEmbedding embeddingOf(SearchProduct product) {
        String textHash = product.getEmbeddingTextHash();
        return productRepository.findEmbedding(product.getProductId())
            .filter(stored -> stored.textHash().equals(textHash))
            .or(() -> textEmbedder.embed(product.getEmbeddingText())
                .map(vector -> new ProductEmbedding(textHash, vector)))
            .orElse(null);
    }

    /**
     * 벡터 없이 저장된 판매중 상품의 벡터를 저장해 둔 문장으로 다시 만든다. 다시 만든 상품 수를 돌려준다.
     * 의미 검색을 끈 환경(키 없음)은 하지 않는다. API를 쓸 수 없는 상품은 건너뛰고, 한 바퀴 돈 뒤 다시 한다.
     * 연속 실패 차단이 열리면 나머지는 API를 부르지 않고 바로 건너뛴다.
     */
    public int refillMissingEmbeddings() {
        if (!textEmbedder.enabled()) {
            return 0;
        }
        List<EmbeddingRefillTarget> targets = productRepository.findOnSaleWithoutEmbedding(refillCursor,
            REFILL_BATCH_SIZE);
        refillCursor = targets.size() < REFILL_BATCH_SIZE ? null : targets.getLast().productId();
        int refilled = 0;
        for (EmbeddingRefillTarget target : targets) {
            Optional<List<Float>> vector = textEmbedder.embed(target.embeddingText());
            if (vector.isPresent()) {
                productRepository.attachEmbedding(target, new ProductEmbedding(target.textHash(), vector.get()));
                refilled++;
            }
        }
        return refilled;
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
            data.brand(),
            data.createdAt(),
            data.sizeFree(),
            toOptionInfos(data.options()),
            toAttributeEntryInfos(data.infoValues()),
            event.occurredAt(),
            data.description());
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
